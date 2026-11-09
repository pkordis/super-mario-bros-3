package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.PlayerTimer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The time bonus steps 10 off and 500 points while the hundreds or tens digit is non-zero, then 1 off and 50 points.
 */
class TimeBonusTest {
    @Test
    @DisplayName("287 left: 28 steps of 500, then 7 of 50, 14 350 points in all")
    void theClockTurnsIntoScore() {
        // Prepare
        final PlayerData playerData = playerDataWithTime(287);
        final PlayerTimer timer = playerData.getPlayerTimer();

        // Execute & Verify
        for (int step = 1; step <= 28; step++) {
            assertThat(TimeBonus.convertStep(playerData)).as("Step %d", step).isTrue();
            assertThat(timer.getTime()).as("Step %d", step).isEqualTo(287 - 10 * step);
        }
        assertThat(playerData.getScore()).as("Tens done").isEqualTo(28 * 500);
        for (int step = 1; step <= 7; step++) {
            assertThat(TimeBonus.convertStep(playerData)).as("Unit step %d", step).isTrue();
            assertThat(timer.getTime()).as("Unit step %d", step).isEqualTo(7 - step);
        }
        assertThat(playerData.getScore()).isEqualTo(14_350);
        assertThat(timer.getTime()).isZero();
    }

    @Test
    @DisplayName("At 000 a step converts nothing and reports that the clock is done")
    void nothingLeftNothingConverted() {
        // Prepare
        final PlayerData playerData = playerDataWithTime(0);

        // Execute
        final boolean stepped = TimeBonus.convertStep(playerData);

        // Verify
        assertThat(stepped).isFalse();
        assertThat(playerData.getScore()).isZero();
        assertThat(playerData.getPlayerTimer().getTime()).isZero();
    }

    @Test
    @DisplayName("A paused timer taken to 000 by the bonus stays paused and shown, without expiring")
    void theBonusDoesNotExpireTheTimer() {
        // Prepare
        final PlayerData playerData = playerDataWithTime(10);
        playerData.getPlayerTimer().pause();

        // Execute
        TimeBonus.convertStep(playerData);

        // Verify
        assertThat(playerData.getPlayerTimer().getTime()).isZero();
        assertThat(playerData.getPlayerTimer().isPaused()).isTrue();
        assertThat(playerData.isTimerShown()).isTrue();
    }

    private static PlayerData playerDataWithTime(final int time) {
        final PlayerData playerData = new PlayerData();
        final PlayerTimer timer = playerData.getPlayerTimer();
        timer.setInitialTime(time);
        timer.start();
        return playerData;
    }
}
