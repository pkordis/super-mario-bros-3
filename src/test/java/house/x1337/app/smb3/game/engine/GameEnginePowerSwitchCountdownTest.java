package house.x1337.app.smb3.game.engine;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static house.x1337.app.smb3.GameConstants.SIMULATION_DT;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.STANDARD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which simulation ticks count the P-switch window down: every tick of running gameplay, through the grid's own tick,
 * and every tick a player halts gameplay (a pipe, a grow), but none while a blocking fade halts it, as between two
 * scenes. Built on an engine that is never started, with the tile rendering left out and the grids mocked.
 */
class GameEnginePowerSwitchCountdownTest {
    private static final int TICKS = 10;

    private final float oneTick = Math.nextUp((float) SIMULATION_DT);
    private final FadingEngine fadingEngine = new FadingEngine();
    private final LevelScenePlayer player = mock(LevelScenePlayer.class);
    private final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final MotionManager.Registry registry = mock(MotionManager.Registry.class);
        doReturn(List.of()).when(registry).getAll();
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == MotionManager.Registry.class) {
                return registry;
            }
            if (type == FadingEngine.class) {
                return fadingEngine;
            }
            if (type == LevelScenePlayer.class) {
                return player;
            }
            return mock(type);
        });
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(32, 12));
        when(levelScene.toCollisionGrid(any())).thenReturn(collisionGrid);
        final ActiveObjectGrid<ActiveLevelObject> activeObjectGrid = mock();
        when(levelScene.toActiveObjectGrid(any())).thenReturn(activeObjectGrid);

        gameEngine = spy(new GameEngine(mock(CameraState.class), new PlayerData()));
        doNothing().when(gameEngine).renderLevelTiles(any());
        gameEngine.setLevelScene(levelScene);
        gameEngine.buildLevel();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Running gameplay counts the window down through the grid's own tick")
    void runningGameplayTicksTheGrid() {
        // Execute
        runTicks();

        // Verify
        verify(collisionGrid, times(TICKS)).tick();
        verify(collisionGrid, never()).countDownPowerSwitchTimeWindow();
    }

    @Test
    @DisplayName("A player halting gameplay, as in a pipe or a grow, leaves the window counting on every tick")
    void aPlayerHaltKeepsTheWindowCounting() {
        // Prepare
        when(player.isHaltingGameplay()).thenReturn(true);

        // Execute
        runTicks();

        // Verify
        verify(collisionGrid, times(TICKS)).countDownPowerSwitchTimeWindow();
        verify(collisionGrid, never()).tick();
    }

    @Test
    @DisplayName("A blocking fade stops the window, even while the player is halting gameplay too")
    void aBlockingFadeStopsTheWindow() {
        // Prepare - the player is in the pipe while the screen fades out and is held black
        when(player.isHaltingGameplay()).thenReturn(true);
        fadingEngine.fadeOut(STANDARD, null);

        // Execute
        runTicks();

        // Verify
        verify(collisionGrid, never()).countDownPowerSwitchTimeWindow();
        verify(collisionGrid, never()).tick();
    }

    private void runTicks() {
        for (int tick = 0; tick < TICKS; tick++) {
            gameEngine.simpleUpdate(oneTick);
        }
    }
}
