package house.x1337.app.smb3.game.engine;

import com.jme3.input.InputManager;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.PlayerTimer;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.game.engine.LevelRunStart.FRESH;
import static house.x1337.app.smb3.game.engine.LevelRunStart.carryOver;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How a run of the level starts: from its start (small, timer at 300), or through a level junction, where the suit and
 * the running timer carry over. Built on an engine that is never started, with the tile
 * rendering and the grids left out.
 */
class GameEngineBuildLevelTest {
    private final PlayerData playerData = new PlayerData();
    private final LevelScenePlayer spawned = mock(LevelScenePlayer.class);
    private final List<LevelScenePlayer> nextSpawns = new ArrayList<>();

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
                return new FadingEngine();
            }
            if (type == LevelScenePlayer.class) {
                return nextSpawns.isEmpty() ? spawned : nextSpawns.removeFirst();
            }
            return mock(type);
        });
        gameEngine = spy(new GameEngine(mock(CameraState.class), playerData));
        doNothing().when(gameEngine).renderLevelTiles(any());
        doNothing().when(gameEngine).setupLevel(any());
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(32, 12));
        gameEngine.setLevelScene(levelScene);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A carried-over start spawns the player with its power-up and leaves the timer running")
    void carryOverKeepsTheSuitAndTheTimer() {
        // Prepare - one second gone
        final PlayerTimer timer = playerData.getPlayerTimer();
        timer.start();
        for (int tick = 0; tick < SIMULATION_FPS; tick++) {
            timer.tick();
        }

        // Execute
        gameEngine.buildLevel(carryOver(RACCOON));

        // Verify
        verify(spawned).setMode(RACCOON);
        assertThat(timer.getTime()).isEqualTo(299);
        assertThat(timer.isActive()).isTrue();
        assertThat(gameEngine.getAllPlayers()).containsExactly(spawned);
    }

    @Test
    @DisplayName("A fresh start spawns the player small and starts the timer over at 300")
    void freshStartsSmallWithTheTimerAt300() {
        // Prepare
        final PlayerTimer timer = playerData.getPlayerTimer();
        timer.start();
        for (int tick = 0; tick < SIMULATION_FPS; tick++) {
            timer.tick();
        }

        // Execute
        gameEngine.buildLevel();

        // Verify
        verify(spawned).setMode(SHRUNK);
        verify(gameEngine).buildLevel(FRESH);
        assertThat(timer.getTime()).isEqualTo(300);
        assertThat(timer.isActive()).isTrue();
    }

    @Test
    @DisplayName("A rebuild retires the previous player's listener and hands its held keys to the new player")
    void aRebuildKeepsTheKeysHeld() {
        // Prepare - the first run's player is holding right and run when the scene is swapped
        final GameEngine handlerEngine = mock(GameEngine.class);
        final InputManager inputManager = mock(InputManager.class);
        when(handlerEngine.getInputManager()).thenReturn(inputManager);
        final PlayerInputHandler firstInput = new PlayerInputHandler(handlerEngine);
        final PlayerInputHandler secondInput = new PlayerInputHandler(handlerEngine);
        final LevelScenePlayer first = mock(LevelScenePlayer.class);
        final LevelScenePlayer second = mock(LevelScenePlayer.class);
        when(first.getInputHandler()).thenReturn(firstInput);
        when(second.getInputHandler()).thenReturn(secondInput);
        nextSpawns.add(first);
        nextSpawns.add(second);
        gameEngine.buildLevel();
        firstInput.onAction(HANDLER_RIGHT, true, 0f);
        firstInput.onAction(HANDLER_RUN, true, 0f);

        // Execute
        gameEngine.buildLevel(carryOver(RACCOON));

        // Verify
        verify(inputManager).removeListener(firstInput);
        assertThat(secondInput.isActive(HANDLER_RIGHT)).isTrue();
        assertThat(secondInput.isActive(HANDLER_RUN)).isTrue();
        assertThat(gameEngine.getAllPlayers()).containsExactly(second);
    }

    @Test
    @DisplayName("The held cards survive an R restart and a level junction, as Inventory_Cards survives levels")
    void cardsSurviveARebuild() {
        // Prepare
        gameEngine.buildLevel();
        playerData.addCard(STAR);
        playerData.addCard(FLOWER);

        // Execute & Verify
        gameEngine.buildLevel();
        assertThat(playerData.getCards()).as("After a fresh restart").containsExactly(STAR, FLOWER, null);
        gameEngine.buildLevel(carryOver(RACCOON));
        assertThat(playerData.getCards()).as("After a level junction").containsExactly(STAR, FLOWER, null);
        assertThat(gameEngine.getPlayerData()).isSameAs(playerData);
    }

    @Test
    @DisplayName("Every run starts the frame counter over and the level's completion with the card uncollected")
    void aRebuildStartsTheCounterAndTheCompletionOver() {
        // Prepare
        gameEngine.buildLevel();
        for (int tick = 0; tick < 13; tick++) {
            gameEngine.getLevelFrameCounter().tick();
        }

        // Execute
        gameEngine.buildLevel(carryOver(RACCOON));

        // Verify
        assertThat(gameEngine.getLevelFrameCounter().getTicks()).isZero();
        verify(gameEngine.getLevelCompletion(), times(2)).reset();
    }
}
