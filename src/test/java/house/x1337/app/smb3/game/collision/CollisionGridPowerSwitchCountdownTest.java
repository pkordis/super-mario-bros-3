package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.event.PowerSwitchExpired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.time.PowerSwitchTimeWindow.TOTAL_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The grid counts the P-switch window down, either as part of its own tick or on its own while a player halts
 * gameplay, and announces the window running out exactly once.
 */
class CollisionGridPowerSwitchCountdownTest {
    private final GameEngine gameEngine = mock(GameEngine.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
        gameEngine,
        powerSwitchTimeWindow
    );

    @BeforeEach
    void prepare() {
        powerSwitchTimeWindow.activate();
    }

    @Test
    @DisplayName("Counting down on its own runs the window out after its full length and announces it once")
    void countingDownOnItsOwnRunsTheWindowOut() {
        // Execute & Verify
        for (int tick = 1; tick < TOTAL_TICKS; tick++) {
            grid.countDownPowerSwitchTimeWindow();
        }
        assertThat(powerSwitchTimeWindow.isActive()).as("One tick before the end").isTrue();
        assertThat(powerSwitchTimeWindow.getTicksRemaining()).isEqualTo(1);
        verify(gameEngine, never()).publish(any(PowerSwitchExpired.class));

        grid.countDownPowerSwitchTimeWindow();
        assertThat(powerSwitchTimeWindow.isActive()).as("On the last tick").isFalse();
        verify(gameEngine, times(1)).publish(any(PowerSwitchExpired.class));

        grid.countDownPowerSwitchTimeWindow();
        verify(gameEngine, times(1)).publish(any(PowerSwitchExpired.class));
    }

    @Test
    @DisplayName("The grid's own tick and the halted count-down share one clock")
    void theTickAndTheHaltedCountDownShareOneClock() {
        // Execute - half the window running, half halted
        for (int tick = 0; tick < TOTAL_TICKS / 2; tick++) {
            grid.tick();
        }
        for (int tick = 0; tick < TOTAL_TICKS / 2; tick++) {
            grid.countDownPowerSwitchTimeWindow();
        }

        // Verify
        assertThat(powerSwitchTimeWindow.isActive()).isFalse();
        verify(gameEngine, times(1)).publish(any(PowerSwitchExpired.class));
    }
}
