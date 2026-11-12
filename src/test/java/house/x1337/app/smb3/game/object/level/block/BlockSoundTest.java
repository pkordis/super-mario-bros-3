package house.x1337.app.smb3.game.object.level.block;

import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.enumeration.SuperMushroomType;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.RewardDispensingLevelObject;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperLeafMotionManager;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.SoundEffect.CANNONBALL;
import static house.x1337.app.smb3.enumeration.SoundEffect.COIN;
import static house.x1337.app.smb3.enumeration.SoundEffect.ONE_UP;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_UP_BLOCK;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sounds a block makes as it hands out what it holds, and the P-switch's press.
 */
class BlockSoundTest {
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Coordinates OFFSET = of(3, 4);

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final CoinRewardMotionManager coinRewardMotionManager = mock(CoinRewardMotionManager.class);
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
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS)).thenReturn(bakedLayerGeometry);
        when(coinRewardMotionManager.spawnCoinReward(any(), any(), any(), anyInt()))
            .thenReturn(new CompletableFuture<>());

        final ImageResource pressedImage = mock(ImageResource.class);
        when(pressedImage.getRgbData()).thenReturn(new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE]);
        final PressedSwitchBlock pressedSwitchBlock = mock(PressedSwitchBlock.class);
        when(pressedSwitchBlock.getImageResource()).thenReturn(pressedImage);

        // Anything else the blocks resolve is an inert stand-in
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == CoinRewardMotionManager.class) {
                return coinRewardMotionManager;
            }
            if (type == PowerSwitchTimeWindow.class) {
                return new PowerSwitchTimeWindow();
            }
            if (type == SoundPlayer.class) {
                return soundPlayer;
            }
            if (type == PressedSwitchBlock.class) {
                return pressedSwitchBlock;
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A coin out of a block plays the coin sound at once, on the hitting thread")
    void aDispensedCoinPlaysTheCoinSound() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        final LevelScenePlayer player = player(playerData);

        // Execute
        block().onCoinDispensed(player);

        // Verify
        verify(soundPlayer, times(1)).play(COIN);
        verify(soundPlayer, never()).play(ONE_UP);
        assertThat(playerData.getCoins()).isEqualTo(1);
    }

    @Test
    @DisplayName("The hundredth coin out of a block also plays the extra-life sound")
    void theHundredthDispensedCoinPlaysTheExtraLifeSound() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        playerData.setCoins(99);

        // Execute
        block().onCoinDispensed(player(playerData));

        // Verify
        verify(soundPlayer, times(1)).play(COIN);
        verify(soundPlayer, times(1)).play(ONE_UP);
    }

    @Test
    @DisplayName("A leaf coming out of a block adds no sound to the bump that dispensed it")
    void aLeafAddsNoSound() {
        // Prepare
        final RewardDispensingLevelObject dispenser = dispenser();
        final LevelScenePlayer player = player(new PlayerData());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(SuperLeaf.class), any(), any()))
            .thenReturn(mock(SuperLeaf.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(SuperLeafMotionManager.class))
            .thenReturn(mock(SuperLeafMotionManager.class));

        // Execute
        dispenser.onSuperLeafDispensed(player);

        // Verify
        verify(soundPlayer, never()).play(any(SoundEffect.class));
    }

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("A mushroom coming out of a block plays the power-up block sound once")
    void aMushroomPlaysThePowerUpBlockSound(final SuperMushroomType mushroomType) {
        // Prepare
        final RewardDispensingLevelObject dispenser = dispenser();
        final LevelScenePlayer player = player(new PlayerData());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(any(Class.class), any(), any()))
            .thenAnswer(invocation -> mock(SuperMushroom.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(SuperMushroomMotionManager.class))
            .thenReturn(mock(SuperMushroomMotionManager.class));

        // Execute
        dispenser.onSuperMushroomDispensed(player, mushroomType);

        // Verify
        verify(soundPlayer, times(1)).play(POWER_UP_BLOCK);
    }

    @Test
    @DisplayName("Pressing the P-switch plays the press sound once, however long the player stands on it")
    void pressingTheSwitchPlaysThePressSound() {
        // Prepare
        final SwitchBlock switchBlock = new SwitchBlock(gameEngine, OFFSET);
        final LevelScenePlayer player = player(new PlayerData());

        // Execute
        switchBlock.onCollisionFromAbove(player);
        switchBlock.onCollisionFromAbove(player);

        // Verify
        verify(soundPlayer, times(1)).play(CANNONBALL);
        verify(switchBlock.getSwitchBlockAnimator()).unregisterAt(OFFSET);
    }

    private Block block() {
        return new Block(gameEngine, mock(ImageResource.class), OFFSET);
    }

    private RewardDispensingLevelObject dispenser() {
        final RewardDispensingLevelObject dispenser = mock(RewardDispensingLevelObject.class);
        when(dispenser.getOffset()).thenReturn(OFFSET);
        when(dispenser.getSoundPlayer()).thenReturn(soundPlayer);
        doCallRealMethod().when(dispenser).onSuperLeafDispensed(any());
        doCallRealMethod().when(dispenser).onSuperMushroomDispensed(any(), any());
        return dispenser;
    }

    private LevelScenePlayer player(final PlayerData playerData) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPlayerData()).thenReturn(playerData);
        when(player.getGameEngine()).thenReturn(gameEngine);
        when(player.getCollisionGrid()).thenReturn(collisionGrid);
        when(collisionGrid.getBumpProbeY()).thenReturn(0);
        return player;
    }

    /** A layer geometry over a real, level-sized texture, so painting into it works for real. */
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
