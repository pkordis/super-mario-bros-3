package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_UP;
import static house.x1337.app.smb3.enumeration.SoundEffect.TANOOKI;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sound a collected power-up plays: the grow or points-only power-up sound, the poof into a raccoon, or the
 * extra life.
 */
class PlayerRewardSoundTest {
    private static final int COLUMNS = 16;
    private static final int ROWS = 14;

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private GameEngine gameEngine;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(mock(CameraState.class));
        when(gameEngine.getFadingEngine()).thenReturn(new FadingEngine());
        when(gameEngine.getRootNode()).thenReturn(new Node("Root"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == SoundPlayer.class) {
                return soundPlayer;
            }
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A small player growing on a mushroom plays the power-up sound once")
    void growingPlaysThePowerUpSound() {
        // Prepare
        final LevelScenePlayer player = player(SHRUNK);

        // Execute
        player.onRewardConsumption(reward(SuperMushroom.class, SCORE_1000));

        // Verify
        verify(player.getSoundPlayer(), times(1)).play(POWER_UP);
    }

    @Test
    @DisplayName("A big player taking a mushroom for points plays the power-up sound too")
    void pointsOnlyPlaysThePowerUpSound() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);

        // Execute
        player.onRewardConsumption(reward(SuperMushroom.class, SCORE_1000));

        // Verify
        verify(player.getSoundPlayer(), times(1)).play(POWER_UP);
    }

    @Test
    @DisplayName("A big player turning raccoon on a leaf plays the poof sound, not the power-up")
    void turningRaccoonPlaysThePoofSound() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);

        // Execute
        player.onRewardConsumption(reward(SuperLeaf.class, SCORE_1000));

        // Verify
        verify(player.getSoundPlayer(), times(1)).play(TANOOKI);
        verify(player.getSoundPlayer(), never()).play(POWER_UP);
    }

    @Test
    @DisplayName("A raccoon taking another leaf plays the power-up sound")
    void aLeafForARaccoonPlaysThePowerUpSound() {
        // Prepare
        final LevelScenePlayer player = player(RACCOON);

        // Execute
        player.onRewardConsumption(reward(SuperLeaf.class, SCORE_1000));

        // Verify
        verify(player.getSoundPlayer(), times(1)).play(POWER_UP);
        verify(player.getSoundPlayer(), never()).play(TANOOKI);
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("A 1-up mushroom plays the extra-life sound and no power-up, whatever the suit")
    void aOneUpPlaysTheExtraLifeSound(final PlayerMode mode) {
        // Prepare
        final LevelScenePlayer player = player(mode);

        // Execute
        player.onRewardConsumption(reward(SuperMushroom.class, ONE_UP));

        // Verify
        verify(player.getSoundPlayer(), times(1)).play(SoundEffect.ONE_UP);
        verify(player.getSoundPlayer(), never()).play(POWER_UP);
    }

    private LevelScenePlayer player(final PlayerMode mode) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        return player;
    }

    private RewardLevelObject reward(final Class<? extends RewardLevelObject> type, final Reward rewardType) {
        final RewardLevelObject reward = mock(type);
        when(reward.getRewardType()).thenReturn(rewardType);
        return reward;
    }
}
