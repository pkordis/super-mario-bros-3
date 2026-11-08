package house.x1337.app.smb3.game.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A paused timer holds its value and stays on the status bar, as the ROM's clock does when {@code Level_TimerEn}
 * stops it; one that never started is not shown.
 */
class PlayerTimerPauseTest {
    @Test
    @DisplayName("A paused timer holds its value and is still shown")
    void aPausedTimerHoldsItsValue() {
        // Prepare
        final PlayerTimer timer = new PlayerTimer();
        timer.start();
        tickSeconds(timer, 2);

        // Execute
        timer.pause();
        tickSeconds(timer, 5);

        // Verify
        assertThat(timer.getTime()).isEqualTo(298);
        assertThat(timer.isActive()).isFalse();
        assertThat(timer.isPaused()).isTrue();
        assertThat(timer.isShown()).isTrue();
    }

    @Test
    @DisplayName("Starting a paused timer resumes it from its value")
    void startResumes() {
        // Prepare
        final PlayerTimer timer = new PlayerTimer();
        timer.start();
        tickSeconds(timer, 1);
        timer.pause();

        // Execute
        timer.start();
        tickSeconds(timer, 1);

        // Verify
        assertThat(timer.getTime()).isEqualTo(298);
        assertThat(timer.isPaused()).isFalse();
        assertThat(timer.isShown()).isTrue();
    }

    @Test
    @DisplayName("Pausing a timer that never started leaves it hidden")
    void pausingAnIdleTimerDoesNothing() {
        // Prepare
        final PlayerTimer timer = new PlayerTimer();

        // Execute
        timer.pause();

        // Verify
        assertThat(timer.isPaused()).isFalse();
        assertThat(timer.isShown()).isFalse();
    }

    private static void tickSeconds(final PlayerTimer timer, final int seconds) {
        for (int tick = 0; tick < seconds * SIMULATION_FPS; tick++) {
            timer.tick();
        }
    }
}
