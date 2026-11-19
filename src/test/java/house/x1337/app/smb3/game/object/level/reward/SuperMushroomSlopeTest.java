package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.collision.SlopeTerrain;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.stream.Stream;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.SuperMushroomType.GROWING_CAPABLE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A Super Mushroom rolling over real ramp tiles, after its rise: it follows the slopes as the ground-walking enemies
 * do, at its own unchanged speed.
 */
@TestInstance(PER_CLASS)
class SuperMushroomSlopeTest {
    private static final double TOLERANCE = 1e-9;
    private static final int BODY_SIZE = 16;
    private static final double ROLL_STEP_PIXELS = 1;

    private final SlopeTerrain steepHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............/##\\........................",
        ".........../####\\.......................",
        "........../######\\......................",
        "########################################"
    );

    private final SlopeTerrain gentleHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............ab####cd....................",
        "..........ab########cd..................",
        "########################################"
    );

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Reward.Data.class))
            .thenAnswer(invocation -> new Reward.Data());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    Stream<Arguments> hills() {
        return Stream.of(
            arguments("steep", steepHill, 7, true, 19),
            arguments("steep, rolling left", steepHill, 22, false, 9),
            arguments("gentle", gentleHill, 7, true, 23),
            arguments("gentle, rolling left", gentleHill, 25, false, 9)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hills")
    @DisplayName("Rolls up and down a hill on its surface every frame, a pixel a frame, without turning back")
    void rollsOverAHill(
        final String name,
        final SlopeTerrain hill,
        final int startColumn,
        final boolean rollingRight,
        final int pastColumn
    ) {
        // Prepare
        final SuperMushroom mushroom = rollingMushroomOn(hill, startColumn, rollingRight);
        final double direction = rollingRight ? 1 : -1;

        // Execute & Verify
        for (int frame = 0; frame < 300; frame++) {
            final double previousX = mushroom.getPosition().x();
            mushroom.motionUpdate();
            final int centreX = (int) Math.floor(mushroom.getPosition().x() + BODY_SIZE / 2.0);
            assertThat(mushroom.getPosition().y() + BODY_SIZE)
                .as("Frame %d: feet on the surface at x %s", frame, mushroom.getPosition().x())
                .isCloseTo(hill.surfaceYAt(centreX), within(TOLERANCE));
            assertThat(mushroom.getPosition().x() - previousX)
                .as("Frame %d: its speed is not changed by the slope", frame)
                .isCloseTo(direction * ROLL_STEP_PIXELS, within(TOLERANCE));
        }
        final double pastX = pastColumn * (double) TILE_SPRITE_SIZE;
        assertThat(rollingRight ? mushroom.getPosition().x() > pastX : mushroom.getPosition().x() < pastX)
            .as("Over the hill")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private SuperMushroom rollingMushroomOn(
        final SlopeTerrain terrain,
        final int column,
        final boolean rollingRight
    ) {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(terrain.columns(), terrain.rowCount()));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(terrain.toGrid(gameEngine));
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));

        final SuperMushroom mushroom = new SuperMushroom(gameEngine, of(column, 0));
        mushroom.setRewardImages(new EnumeratedImageResource<>(Map.of(
            SuperMushroomImageResource.GROWING_CAPABLE, image(),
            SuperMushroomImageResource.LIFE_AWARDING_GREEN, image()
        )));
        mushroom.configure((SuperMushroomVariantData) GROWING_CAPABLE.asLevelObjectType().getVariantData());

        // Done rising, resting on the floor and already rolling
        mushroom.setRising(false);
        final int floorY = terrain.surfaceYAt(column * TILE_SPRITE_SIZE + BODY_SIZE / 2);
        mushroom.setPosition(mushroom.getPosition().withY(floorY - BODY_SIZE));
        mushroom.setFallDirectionRight(rollingRight);
        mushroom.setGrounded(true);
        return mushroom;
    }

    private ImageResource image() {
        final ImageResource image = mock(ImageResource.class);
        when(image.getDimensions()).thenReturn(new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE));
        when(image.asTexture()).thenReturn(new Texture2D(new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4),
            sRGB
        )));
        return image;
    }
}
