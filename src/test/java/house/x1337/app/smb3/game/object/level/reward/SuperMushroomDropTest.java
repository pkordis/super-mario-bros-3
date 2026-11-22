package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.SuperMushroomType;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A block mushroom (Super or 1-Up) dropped out of the bottom of its block: it starts in the lower half of the block,
 * shown and not rising, and falls out to the ground it lands on, where it walks away. The block, a note block the
 * player has landed on, is the only terrain but for the floor below.
 */
class SuperMushroomDropTest {
    private static final double TOLERANCE = 1e-6;
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Coordinates BLOCK = of(3, 2);
    private static final int FLOOR_ROW = 6;
    private static final double BLOCK_X = BLOCK.x() * (double) TILE_SPRITE_SIZE;
    private static final double BLOCK_TOP_Y = BLOCK.y() * (double) TILE_SPRITE_SIZE;
    private static final double FLOOR_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE;
    private static final int HOLD_FRAMES = 16;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private SuperMushroomMotionManager manager;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        manager = new SuperMushroomMotionManager();

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // The note block and the floor are the terrain, and the block is not mid-bump from the mushroom's view.
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(any())).thenAnswer(invocation -> {
            final Coordinates cell = invocation.getArgument(0);
            return isSolid(cell.x(), cell.y());
        });
        when(collisionGrid.isWallOrCeilingSolidTile(any())).thenAnswer(invocation -> {
            final Coordinates cell = invocation.getArgument(0);
            return isSolid(cell.x(), cell.y());
        });
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(-1000, -1000, 1000, 1000));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Reward.Data.class))
            .thenAnswer(invocation -> new Reward.Data());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("It starts half a tile into the block, shown whole and not rising")
    void itStartsInTheLowerHalfOfTheBlock(final SuperMushroomType variant) {
        // Prepare & Execute
        final SuperMushroom mushroom = dropped(variant);

        // Verify
        assertThat(mushroom.isRising()).isFalse();
        assertThat(mushroom.getPosition().x()).isCloseTo(BLOCK_X, within(TOLERANCE));
        assertThat(mushroom.getPosition().y()).isCloseTo(BLOCK_TOP_Y + 8, within(TOLERANCE));
        assertThat(quadBottom(mushroom)).as("Not clipped").isCloseTo(0f, within((float) TOLERANCE));
    }

    @Test
    @DisplayName("It falls straight down, with no hold and no rise, until it lands on the floor")
    void itFallsOutAndLands() {
        // Prepare
        final SuperMushroom mushroom = dropped(SuperMushroomType.GROWING_CAPABLE);
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);
        double previousY = mushroom.getPosition().y();

        // Execute & Verify
        for (int frame = 0; frame < 40 && !mushroom.isGrounded(); frame++) {
            tick(farAwayPlayer);
            assertThat(mushroom.getPosition().y()).as("Frame %d: falling", frame).isGreaterThan(previousY);
            assertThat(mushroom.getPosition().x()).as("Frame %d: straight down", frame)
                .isCloseTo(BLOCK_X, within(TOLERANCE));
            previousY = mushroom.getPosition().y();
        }
        assertThat(mushroom.isGrounded()).as("Landed").isTrue();
        assertThat(mushroom.getPosition().y()).as("On the floor").isCloseTo(FLOOR_Y - 16, within(TOLERANCE));
    }

    @Test
    @DisplayName("Once landed it walks away across the floor, as a mushroom that came out of the top does")
    void itWalksAwayOnceLanded() {
        // Prepare
        final SuperMushroom mushroom = dropped(SuperMushroomType.GROWING_CAPABLE);
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute
        for (int frame = 0; frame < 60; frame++) {
            tick(farAwayPlayer);
        }

        // Verify
        assertThat(mushroom.getPosition().x()).as("Moved sideways").isNotCloseTo(BLOCK_X, within(8.0));
        assertThat(mushroom.getPosition().y()).as("On the floor").isCloseTo(FLOOR_Y - 16, within(TOLERANCE));
    }

    @Test
    @DisplayName("It can be collected once its 16 protection frames have run, with no hold on top of them")
    void itIsCollectableAfterTheProtection() {
        // Prepare
        final SuperMushroom mushroom = dropped(SuperMushroomType.GROWING_CAPABLE);
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute & Verify
        for (int frame = 1; frame <= HOLD_FRAMES; frame++) {
            tick(farAwayPlayer);
            assertThat(mushroom.isCollectable()).as("Frame %d", frame).isEqualTo(frame >= HOLD_FRAMES);
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private SuperMushroom dropped(final SuperMushroomType variant) {
        final ImageResource image = mock(ImageResource.class);
        when(image.getDimensions()).thenReturn(new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE));
        final Texture2D texture = new Texture2D(new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4),
            sRGB
        ));
        when(image.asTexture()).thenReturn(texture);

        final SuperMushroom mushroom = new SuperMushroom(gameEngine, BLOCK);
        mushroom.setRewardImages(new EnumeratedImageResource<>(Map.of(
            SuperMushroomImageResource.GROWING_CAPABLE, image,
            SuperMushroomImageResource.LIFE_AWARDING_GREEN, image
        )));
        // As RewardDispensingLevelObject.onSuperMushroomDispensed does it for a block struck from above
        mushroom.configure((SuperMushroomVariantData) variant.asLevelObjectType().getVariantData());
        mushroom.dropOutOfTheBlockBelow();
        manager.spawn(mushroom);
        return mushroom;
    }

    private void tick(final LevelScenePlayer player) {
        broadPhase.clear();
        manager.update();
        broadPhase.resolveActiveObjectCollisions(List.of(player));
    }

    private LevelScenePlayer playerAt(final double x, final double y) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getObjectCollisionBounds()).thenReturn(new AxisAlignedBoundingBox(x + 3, y + 5, x + 13, y + 30));
        when(player.getPlayerData()).thenReturn(new PlayerData());
        return player;
    }

    private boolean isSolid(final int column, final int row) {
        return column == BLOCK.x() && row == BLOCK.y() || row == FLOOR_ROW;
    }

    private float quadBottom(final SuperMushroom mushroom) {
        final FloatBuffer positions = mushroom.getSpriteGeometry()
            .getMesh()
            .getFloatBuffer(VertexBuffer.Type.Position);
        return positions.get(1);
    }
}