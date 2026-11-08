package house.x1337.app.smb3.event;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.event.LevelCompleted;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A finished run-off restarts the level by the path R takes: {@code requestLevelSceneRestart}.
 */
class TestSessionEventListenerLevelCompletedTest {
    @Test
    @DisplayName("LevelCompleted asks the session to restart the level, as R does")
    void levelCompletedRestartsTheLevel() {
        // Prepare
        final GameEventBus bus = new GameEventBus();
        final RecordingSession session = new RecordingSession(bus);
        session.subscribeListeners();

        // Execute
        bus.publish(new LevelCompleted());

        // Verify
        assertThat(session.restartRequests).isEqualTo(1);
    }

    private static final class RecordingSession implements TestSessionEventListener {
        private final GameEventBus bus;
        private int restartRequests;

        private RecordingSession(final GameEventBus bus) {
            this.bus = bus;
        }

        @Override
        public GameEventBus getGameEventBus() {
            return bus;
        }

        @Override
        public GameEngine getGameEngine() {
            return null;
        }

        @Override
        public void requestLevelSceneRestart() {
            restartRequests++;
        }

        @Override
        public void onGameEngineStopped() {
        }

        @Override
        public void onLevelSceneRestart() {
        }

        @Override
        public void onPipePortalTransitRequested(final PipePortalTransitRequested event) {
        }
    }
}
