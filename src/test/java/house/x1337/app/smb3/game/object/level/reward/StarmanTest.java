package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.motion.StarmanMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The star: it leaves its block with a hop, bounces off the floor every time it lands, turns at walls, drops back
 * from ceilings, flips between its two sprites, and gives the invincibility when it is collected.
 */
class StarmanTest {
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final Coordinates BLOCK = of(3, 2);
    private static final int FLOOR_ROW = 7;
    private static final double FLOOR_REST_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE - TILE_SPRITE_SIZE;
    private static final int RISE_FRAMES = 61;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private StarmanMotionManager manager;
    private GameEngine gameEngine;
    private int wallColumn = -1;
    private int ceilingRow = -1;

    @BeforeEach
    void prepare() {
        broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        manager = new StarmanMotionManager();

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

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

    @Test
    @DisplayName("When it has risen out of the block it hops up and away from where it came out")
    void itHopsOutOfTheBlock() {
        // Prepare
        final Starman star = star();
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute
        for (int frame = 0; frame < RISE_FRAMES; frame++) {
            tick(farAwayPlayer);
        }

        // Verify
        assertThat(star.isRising()).isFalse();
        assertThat(star.getVelocity()).isEqualTo(new FixedPointVelocity(16, -56));
    }

    @Test
    @DisplayName("Dropped out of the bottom of a block it moves sideways from the first frame")
    void aDroppedStarMovesFromTheFirstFrame() {
        // Prepare
        final Starman star = star();

        // Execute
        star.dropOutOfTheBlockBelow();

        // Verify
        assertThat(star.getVelocity()).isEqualTo(new FixedPointVelocity(16, 16));
    }

    @Test
    @DisplayName("Every time it lands it bounces up at 4 pixels a frame, and goes on bouncing")
    void itBouncesOnEveryLanding() {
        // Prepare
        final Starman star = star();
        star.dropOutOfTheBlockBelow();
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute & Verify
        int landings = 0;
        for (int frame = 0; frame < 400 && landings < 3; frame++) {
            tick(farAwayPlayer);
            if (star.isGrounded()) {
                landings++;
                assertThat(star.getVelocity().y()).as("Landing %d", landings).isEqualTo(-64);
                assertThat(star.getPosition().y()).as("On the floor").isEqualTo(FLOOR_REST_Y);
            }
        }
        assertThat(landings).isEqualTo(3);
    }

    @Test
    @DisplayName("It keeps its horizontal speed through the bounces")
    void itKeepsMovingSideways() {
        // Prepare
        final Starman star = star();
        star.dropOutOfTheBlockBelow();
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);
        final double startX = star.getPosition().x();

        // Execute
        for (int frame = 0; frame < 50; frame++) {
            tick(farAwayPlayer);
        }

        // Verify
        assertThat(star.getPosition().x()).isEqualTo(startX + 50);
    }

    @Test
    @DisplayName("It turns round at a wall")
    void itTurnsAtAWall() {
        // Prepare
        wallColumn = 6;
        final Starman star = star();
        star.dropOutOfTheBlockBelow();
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute
        for (int frame = 0; frame < 120; frame++) {
            tick(farAwayPlayer);
        }

        // Verify
        assertThat(star.getVelocity().x()).isEqualTo(-16);
        assertThat(star.getPosition().x()).isLessThan(wallColumn * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Hitting a ceiling on the way up turns it round to fall")
    void itFallsBackFromACeiling() {
        // Prepare
        ceilingRow = 3;
        final Starman star = star();
        star.dropOutOfTheBlockBelow();
        star.setPosition(new PixelPosition(48, 80));
        star.setVelocity(new FixedPointVelocity(16, -56));
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute
        int frames = 0;
        while (star.getVelocity().y() < 0 && frames < 30) {
            tick(farAwayPlayer);
            frames++;
        }

        // Verify
        assertThat(star.getVelocity().y()).isEqualTo(4);
        assertThat(star.getPosition().y()).as("It did not enter the ceiling").isGreaterThanOrEqualTo(64);
    }

    @Test
    @DisplayName("It flips between its two sprites while it bounces")
    void itFlipsBetweenItsSprites() {
        // Prepare
        final Starman star = star();
        star.dropOutOfTheBlockBelow();
        final LevelScenePlayer farAwayPlayer = playerAt(0, 0);

        // Execute & Verify
        final Set<Object> shown = new HashSet<>();
        for (int frame = 0; frame < 8; frame++) {
            tick(farAwayPlayer);
            shown.add(star.getSpriteGeometry().getMaterial().getTextureParam("ColorMap").getTextureValue());
        }
        assertThat(shown).as("Both sprites showed").hasSize(2);
    }

    @Test
    @DisplayName("Collecting it gives the player the invincibility and the points, and no growth")
    void collectingItStartsTheInvincibility() {
        // Prepare
        final Starman star = star();
        star.dropOutOfTheBlockBelow();
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        final PlayerData playerData = new PlayerData();
        when(player.getPlayerData()).thenReturn(playerData);
        when(player.getObjectCollisionBounds()).thenReturn(new AxisAlignedBoundingBox(0, 0, 300, 300));

        // Execute
        for (int frame = 0; frame < 20; frame++) {
            tick(player);
        }

        // Verify
        assertThat(star.isCollected()).isTrue();
        verify(player).startStarInvincibility();
        assertThat(playerData.getScore()).isEqualTo(1000);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Starman star() {
        final Starman star = new Starman(gameEngine, BLOCK);
        star.setImageResource(image());
        star.setRedImageResource(image());
        star.init();
        manager.spawn(star);
        return star;
    }

    private ImageResource image() {
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
        return image;
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
        return row == FLOOR_ROW || row == ceilingRow || column == wallColumn && row < FLOOR_ROW;
    }
}
