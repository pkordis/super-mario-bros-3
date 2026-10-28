package house.x1337.app.smb3.ui.editor.level.tester;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PlayerDied;
import house.x1337.app.smb3.ui.editor.level.pane.MiddlePane;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How a tester session turns R and a death into a level restart: both events are consumed by the same
 * restart, which is queued for the engine's next frame instead of run on the spot, and requests that land
 * before the queued one has run collapse into a single rebuild.
 */
class LevelSceneTesterSessionRestartTest {
    private final List<Runnable> engineQueue = new ArrayList<>();

    private GameEventBus gameEventBus;
    private GameEngine gameEngine;
    private Node rootNode;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        gameEventBus = new GameEventBus();
        rootNode = new Node("Root");
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getRootNode()).thenReturn(rootNode);
        doAnswer(invocation -> engineQueue.add(invocation.getArgument(0)))
            .when(gameEngine)
            .enqueue(any(Runnable.class));
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GameEventBus.class))
            .thenReturn(gameEventBus);
        session().subscribeListeners();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A reset request queues the restart for the next frame instead of rebuilding on the spot")
    void aResetRequestIsQueued() {
        // Execute
        gameEventBus.publish(resetRequest());

        // Verify - nothing is rebuilt from inside the publishing frame
        assertThat(engineQueue).hasSize(1);
        verify(gameEngine, never()).buildLevel();

        // Execute - the engine's next frame
        runEngineQueue();

        // Verify
        verify(gameEngine, times(1)).buildLevel();
    }

    @Test
    @DisplayName("The restart detaches the previous run's player, camera target and layers before rebuilding")
    void theRestartClearsThePreviousRun() {
        // Prepare
        rootNode.attachChild(new Node("PlayerNode"));
        rootNode.attachChild(new Node("CameraTarget"));
        rootNode.attachChild(new Node(AIR.getLayerName()));

        // Execute
        gameEventBus.publish(resetRequest());
        runEngineQueue();

        // Verify
        assertThat(rootNode.getChildren()).isEmpty();
        verify(gameEngine, times(1)).buildLevel();
    }

    @Test
    @DisplayName("A death and a reset request in the same window rebuild the level once")
    void aDeathAndAResetCollapse() {
        // Execute - R pressed during the death throe, whose PlayerDied lands before the restart runs
        gameEventBus.publish(resetRequest());
        gameEventBus.publish(new PlayerDied());
        runEngineQueue();

        // Verify
        verify(gameEngine, times(1)).buildLevel();
    }

    @Test
    @DisplayName("Two quick reset requests rebuild the level once")
    void twoQuickResetsCollapse() {
        // Execute
        gameEventBus.publish(resetRequest());
        gameEventBus.publish(resetRequest());

        // Verify
        assertThat(engineQueue).as("The second request finds one already pending").hasSize(1);

        // Execute
        runEngineQueue();

        // Verify
        verify(gameEngine, times(1)).buildLevel();
    }

    @Test
    @DisplayName("Once a restart has run, the next request restarts the level again")
    void eachPressAfterARestartIsHonoured() {
        // Prepare
        gameEventBus.publish(resetRequest());
        runEngineQueue();

        // Execute
        gameEventBus.publish(resetRequest());
        runEngineQueue();

        // Verify
        verify(gameEngine, times(2)).buildLevel();
    }

    @Test
    @DisplayName("A restart that fails still clears the pending flag, so the next request is not swallowed")
    void aFailedRestartDoesNotWedgeTheGuard() {
        // Prepare
        doAnswer(invocation -> {
            throw new IllegalStateException("Rebuild failed");
        }).doNothing().when(gameEngine).buildLevel();
        gameEventBus.publish(resetRequest());
        try {
            runEngineQueue();
        } catch (final IllegalStateException expected) {
            // The engine logs a failing queued task; all that matters here is what happens next
        }

        // Execute
        gameEventBus.publish(resetRequest());

        // Verify
        assertThat(engineQueue).hasSize(1);
    }

    @Test
    @DisplayName("A death on its own still restarts the level, as it did before R existed")
    void aDeathStillRestarts() {
        // Execute
        gameEventBus.publish(new PlayerDied());
        runEngineQueue();

        // Verify
        verify(gameEngine, times(1)).buildLevel();
    }

    @Test
    @DisplayName("Without a tester session subscribed, a reset request does nothing")
    void withoutASessionNothingHappens() {
        // Prepare - a bus no session has subscribed to
        final GameEventBus unattended = new GameEventBus();

        // Execute
        unattended.publish(resetRequest());

        // Verify
        assertThat(engineQueue).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A session without its Swing build-out; only the engine takes part in a restart. */
    private LevelSceneTesterSession session() {
        return new LevelSceneTesterSession(
            mock(LevelSceneEditorTabSystem.class),
            mock(DebuggerPanel.class),
            mock(MiddlePane.class),
            gameEngine
        );
    }

    private static LevelSceneResetRequested resetRequest() {
        return LevelSceneResetRequested.builder().inputHandlerId("handler-1").build();
    }

    /** Runs what the engine would run at the start of its next frame. */
    private void runEngineQueue() {
        final List<Runnable> tasks = new ArrayList<>(engineQueue);
        engineQueue.clear();
        tasks.forEach(Runnable::run);
    }
}
