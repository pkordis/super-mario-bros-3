package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PowerSwitchExpired;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A death closes an open P-switch window the moment it starts, whichever way the player dies, without announcing
 * the window's end: the death music silences the level anyway.
 */
class PlayerDeathPowerSwitchTest {
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private GameEngine gameEngine;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(16, 14));
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.getPowerSwitchTimeWindow()).thenReturn(powerSwitchTimeWindow);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getCameraState()).thenReturn(mock(CameraState.class));
        when(gameEngine.getFadingEngine()).thenReturn(new FadingEngine());
        when(gameEngine.getRootNode()).thenReturn(new Node("Root"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
        powerSwitchTimeWindow.activate();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A death by a hit closes the P-switch window as it starts")
    void aHurtDeathClosesTheWindow() {
        // Prepare
        final LevelScenePlayer player = smallPlayer();

        // Execute
        player.die();

        // Verify
        assertThat(powerSwitchTimeWindow.isActive()).isFalse();
        verify(gameEngine).publish(any(PlayerDeathStarted.class));
        verify(gameEngine, never()).publish(any(PowerSwitchExpired.class));
    }

    @Test
    @DisplayName("A death by falling into a pit closes the P-switch window as it starts")
    void aPitDeathClosesTheWindow() {
        // Prepare
        final LevelScenePlayer player = smallPlayer();

        // Execute
        player.dieByFallingOff();

        // Verify
        assertThat(powerSwitchTimeWindow.isActive()).isFalse();
        verify(gameEngine).publish(any(PlayerDeathStarted.class));
        verify(gameEngine, never()).publish(any(PowerSwitchExpired.class));
    }

    private LevelScenePlayer smallPlayer() {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(SHRUNK);
        return player;
    }
}
