package house.x1337.app.smb3.game.object.level.block.motion;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.animation.NoteBlockBounceAnimation;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A note block bounces up when struck and knocks what stands on it; landed on, it sinks with the player riding it and
 * launches them, harder when a jump was pressed during the ride.
 */
class NoteBlockBounceMotionManagerTest {
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Coordinates OFFSET = of(2, 3);
    private static final float BLOCK_WORLD_Y = ROWS - 1 - OFFSET.y();
    private static final double TOLERANCE = 1e-9;

    private static final int BOUNCE_TICKS = 12;
    private static final double[] RIDER_Y_VELOCITIES = {0, 4, 3, 2, 1, 0, -1, -2, -3, -4, -4};
    private static final int[] SINKING_HEIGHTS = {0, 0, -4, -7, -9, -10, -10, -9, -7, -4, 0};

    private final NoteBlockBounceMotionManager manager = new NoteBlockBounceMotionManager();
    private final GameObjectAnimatorSingleTiled<?> animator = mock(GameObjectAnimatorSingleTiled.class);
    private final PlayerPosition position = new PlayerPosition();
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private Node rootNode;
    private GameEngine gameEngine;
    private LevelScenePlayer rider;

    @BeforeEach
    void prepare() {
        rootNode = new Node("root");
        gameEngine = gameEngine();
        rider = mock(LevelScenePlayer.class);
        when(rider.getPosition()).thenReturn(position);
        when(rider.getRuntimeState()).thenReturn(runtimeState);
        // The animations are prototype beans; the bean factory hands out real ones built from the same arguments
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(NoteBlockBounceAnimation.class), any(), any()))
            .thenAnswer(invocation -> new NoteBlockBounceAnimation(
                invocation.getArgument(1),
                invocation.getArgument(2)
            ));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A block struck from below bounces up and knocks what stands on it until the tick the bounce ends")
    void aStruckBlockBouncesUpAndKnocks() {
        // Prepare
        manager.spawnBounceUp(gameEngine, OFFSET, animator);

        // Execute & Verify
        verify(animator).pauseAt(OFFSET);
        for (int tick = 1; tick <= BOUNCE_TICKS; tick++) {
            assertThat(manager.isBlockBumpActiveAt(OFFSET)).as("Knocking before tick %d", tick).isTrue();
            assertThat(manager.isSinkingAt(OFFSET)).as("An upward bounce does not let players through").isFalse();
            manager.update();
        }
        assertThat(rootNode.getChildren()).as("The moving block is gone").isEmpty();
        verify(animator).resumeAt(OFFSET);
        assertThat(manager.isBlockBumpActiveAt(OFFSET)).as("Still knocking for the rest of the last tick").isTrue();

        manager.postCollision();
        assertThat(manager.isBounceActiveAt(OFFSET)).as("The bounce is over").isFalse();
        assertThat(manager.isBlockBumpActiveAt(OFFSET)).isFalse();
    }

    @Test
    @DisplayName("A block landed on sinks and comes back up, and never knocks what stands on it")
    void aLandedOnBlockSinksWithoutKnocking() {
        // Prepare
        manager.spawnBounceDown(gameEngine, OFFSET, animator, rider);
        final Spatial sprite = rootNode.getChild(0);

        // Execute & Verify
        for (int tick = 0; tick < SINKING_HEIGHTS.length; tick++) {
            assertThat(manager.isBlockBumpActiveAt(OFFSET)).as("A sinking block does not knock").isFalse();
            assertThat(manager.isSinkingAt(OFFSET)).as("Sinking before tick %d", tick + 1).isTrue();
            tick();
            assertThat(sprite.getLocalTranslation().getY())
                .as("Height of the block after tick %d", tick + 1)
                .isEqualTo(BLOCK_WORLD_Y + (float) SINKING_HEIGHTS[tick] / TILE_SPRITE_SIZE);
        }
        tick();
        assertThat(manager.isSinkingAt(OFFSET)).as("The bounce is over").isFalse();
        assertThat(rootNode.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("The rider moves with the block every tick, and is launched as the bounce ends")
    void theRiderMovesWithTheBlockThenIsLaunched() {
        // Prepare
        position.setDY(0);
        runtimeState.stop();

        // Execute
        manager.spawnBounceDown(gameEngine, OFFSET, animator, rider);

        // Verify
        assertThat(runtimeState.isInAir()).as("The rider leaves the ground").isTrue();
        assertThat(runtimeState.getMovement()).isEqualTo(FALLING);
        assertThat(runtimeState.isRidingNoteBlock()).isTrue();
        for (int tick = 0; tick < RIDER_Y_VELOCITIES.length; tick++) {
            tick();
            assertThat(position.getDY())
                .as("Rider's DY after tick %d", tick + 1)
                .isCloseTo(RIDER_Y_VELOCITIES[tick], within(TOLERANCE));
            assertThat(runtimeState.isRidingNoteBlock())
                .as("Riding after tick %d, until the block is back in place", tick + 1)
                .isEqualTo(tick < RIDER_Y_VELOCITIES.length - 1);
        }
        tick();
        assertThat(position.getDY()).as("The launch").isCloseTo(-3.5, within(TOLERANCE));
        assertThat(runtimeState.getMovement()).isEqualTo(JUMPING);
        assertThat(runtimeState.isRidingNoteBlock()).as("The ride is over").isFalse();
        assertThat(runtimeState.isSlowGravityBlocked()).as("Holding the jump does not stretch the launch").isTrue();
    }

    @Test
    @DisplayName("A jump pressed during the ride launches the rider twice as fast")
    void aBoostedRideLaunchesTwiceAsFast() {
        // Prepare
        manager.spawnBounceDown(gameEngine, OFFSET, animator, rider);
        for (int tick = 0; tick < 5; tick++) {
            tick();
        }
        runtimeState.setNoteBlockBoosted(true);

        // Execute
        for (int tick = 5; tick < BOUNCE_TICKS; tick++) {
            tick();
        }

        // Verify
        assertThat(position.getDY()).isCloseTo(-7.0, within(TOLERANCE));
        assertThat(runtimeState.isNoteBlockBoosted()).as("The boost is spent").isFalse();
    }

    @Test
    @DisplayName("A block already bouncing does not start over when it is landed on or struck again")
    void aBouncingBlockDoesNotRestart() {
        // Prepare
        manager.spawnBounceDown(gameEngine, OFFSET, animator, rider);
        tick();

        // Execute
        manager.spawnBounceDown(gameEngine, OFFSET, animator, mock(LevelScenePlayer.class));
        manager.spawnBounceUp(gameEngine, OFFSET, animator);

        // Verify
        assertThat(rootNode.getChildren()).as("One moving block").hasSize(1);
        assertThat(manager.isBlockBumpActiveAt(OFFSET)).as("Still the sinking bounce").isFalse();
    }

    @Test
    @DisplayName("Restarting the level drops the bounces and ends the ride without a launch")
    void resetEndsTheRide() {
        // Prepare
        manager.spawnBounceDown(gameEngine, OFFSET, animator, rider);
        tick();
        tick();
        runtimeState.setNoteBlockBoosted(true);

        // Execute
        manager.reset();

        // Verify
        assertThat(rootNode.getChildren()).isEmpty();
        assertThat(manager.isBounceActiveAt(OFFSET)).isFalse();
        assertThat(runtimeState.isRidingNoteBlock()).isFalse();
        assertThat(runtimeState.isNoteBlockBoosted()).isFalse();
        assertThat(position.getDY()).as("No launch").isCloseTo(RIDER_Y_VELOCITIES[1], within(TOLERANCE));
    }

    /** One engine step, as far as the manager sees it. */
    private void tick() {
        manager.update();
        manager.postCollision();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private GameEngine gameEngine() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        final Geometry bakedLayerGeometry = bakedLayerGeometry();
        final GameEngine engine = mock(GameEngine.class);
        when(engine.getRootNode()).thenReturn(rootNode);
        when(engine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(engine.getLevelScene()).thenReturn(levelScene);
        when(engine.getLayerGeometry(INTERACTIVE_OBJECTS)).thenReturn(bakedLayerGeometry);
        return engine;
    }

    private Geometry bakedLayerGeometry() {
        final int width = COLUMNS * TILE_SPRITE_SIZE;
        final int height = ROWS * TILE_SPRITE_SIZE;
        final Image image = new Image(RGBA8, width, height, createByteBuffer(width * height * 4), sRGB);
        final MatParamTexture textureParam = new MatParamTexture(
            VarType.Texture2D,
            "ColorMap",
            new Texture2D(image),
            sRGB
        );
        final Material material = mock(Material.class);
        when(material.getTextureParam(anyString())).thenReturn(textureParam);
        final Geometry geometry = mock(Geometry.class);
        when(geometry.getMaterial()).thenReturn(material);
        return geometry;
    }
}
