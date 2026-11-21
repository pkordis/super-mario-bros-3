package house.x1337.app.smb3.game.object.level.block;

import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.service.LevelObjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A block that bounces because the player's head or tail struck it pushes the player down and off the ground. A
 * block that breaks, or one a kicked object struck, pushes nobody.
 */
class BlockBumpPushTest {
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Coordinates OFFSET = of(3, 4);
    private static final double TOLERANCE = 1e-9;

    private final CoinRewardMotionManager coinRewardMotionManager = mock(CoinRewardMotionManager.class);
    private final LevelObjectService levelObjectService = mock(LevelObjectService.class);
    private final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        final Geometry bakedLayerGeometry = bakedLayerGeometry();
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS)).thenReturn(bakedLayerGeometry);
        when(collisionGrid.getSourceLayerAt(any())).thenReturn(INTERACTIVE_OBJECTS);
        when(coinRewardMotionManager.spawnCoinReward(any(), any(), any(), anyInt()))
            .thenReturn(new CompletableFuture<>());

        final ImageResource emptyBlockImage = mock(ImageResource.class);
        when(emptyBlockImage.getRgbData()).thenReturn(new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE]);
        final EmptyBlock emptyBlock = mock(EmptyBlock.class);
        when(emptyBlock.getImageResource()).thenReturn(emptyBlockImage);
        when(levelObjectService.createEmptyBlock(any(), any())).thenReturn(emptyBlock);

        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == CoinRewardMotionManager.class) {
                return coinRewardMotionManager;
            }
            if (type == LevelObjectService.class) {
                return levelObjectService;
            }
            if (type == PowerSwitchTimeWindow.class) {
                return new PowerSwitchTimeWindow();
            }
            // A reward caches its data the first time it is asked, for the whole run: it must be a real one
            if (type == Reward.Data.class) {
                return new Reward.Data();
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("A ? block struck by the head pushes the player down, small or big")
    void aQuestionBlockStruckByTheHeadPushes(final boolean large) {
        // Prepare
        final LevelScenePlayer player = player(large);

        // Execute
        block("QUESTION_BLOCK").onCollisionFromBelow(player);

        // Verify
        verify(player).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("A brick a small player bumps pushes them down")
    void aBumpedBrickPushes() {
        // Prepare
        final LevelScenePlayer player = player(false);

        // Execute
        block("BRICK_BLOCK_NO_REWARD").onCollisionFromBelow(player);

        // Verify
        verify(player).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("A brick a big player breaks pushes nobody")
    void aBrokenBrickDoesNotPush() {
        // Prepare
        final LevelScenePlayer player = player(true);

        // Execute
        block("BRICK_BLOCK_NO_REWARD").onCollisionFromBelow(player);

        // Verify
        verify(player, never()).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("A tail that bounces a ? block pushes the player; a tail that breaks a brick does not")
    void aTailPushesOnlyWhenTheBlockBounces() {
        // Prepare
        final LevelScenePlayer bouncing = player(true);
        final LevelScenePlayer breaking = player(true);

        // Execute
        block("QUESTION_BLOCK").onTailAttack(bouncing);
        block("BRICK_BLOCK_NO_REWARD").onTailAttack(breaking);

        // Verify
        verify(bouncing).bounceOffBumpedBlock();
        verify(breaking, never()).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("A kicked shell that bounces a ? block pushes nobody")
    void aKickedShellDoesNotPush() {
        // Prepare
        final LevelScenePlayer player = player(false);

        // Execute
        block("QUESTION_BLOCK").onKickedObjectStrike(player);

        // Verify
        verify(levelObjectService).createEmptyBlock(gameEngine, OFFSET);
        verify(player, never()).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("The push sends a grounded player down at 2 px a frame and off the ground")
    void thePushTakesThePlayerOffTheGround() {
        // Prepare
        final PlayerPosition position = new PlayerPosition();
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.stop();
        final LevelScenePlayer player = realPushPlayer(position, runtimeState);

        // Execute
        player.bounceOffBumpedBlock();

        // Verify
        assertThat(position.getDY()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(runtimeState.getMovement()).isEqualTo(FALLING);
    }

    @Test
    @DisplayName("The push sends a rising player down at 2 px a frame")
    void thePushTurnsARisingPlayerDown() {
        // Prepare
        final PlayerPosition position = new PlayerPosition();
        position.setDY(-3.0);
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING);
        final LevelScenePlayer player = realPushPlayer(position, runtimeState);

        // Execute
        player.bounceOffBumpedBlock();

        // Verify
        assertThat(position.getDY()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(runtimeState.isInAir()).isTrue();
    }

    private Block block(final String blockType) {
        final Block block = new Block(gameEngine, mock(ImageResource.class), OFFSET);
        block.configure(new LevelObjectData(Map.of("blockType", blockType)));
        return block;
    }

    private LevelScenePlayer player(final boolean large) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPlayerData()).thenReturn(new PlayerData());
        when(player.getGameEngine()).thenReturn(gameEngine);
        when(player.getCollisionGrid()).thenReturn(collisionGrid);
        when(player.isLarge()).thenReturn(large);
        return player;
    }

    private LevelScenePlayer realPushPlayer(final PlayerPosition position, final PlayerRuntimeState runtimeState) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        doCallRealMethod().when(player).bounceOffBumpedBlock();
        return player;
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
