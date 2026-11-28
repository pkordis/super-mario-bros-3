package house.x1337.app.smb3.model.game.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The star invincibility's counter. */
class PlayerRuntimeStateStarTest {
    private final PlayerRuntimeState state = new PlayerRuntimeState();

    @Test
    @DisplayName("Starting the invincibility sets the counter to 224")
    void startingSetsTheCounter() {
        // Execute
        state.startStarInvincibility();

        // Verify
        assertThat(state.isStarInvincible()).isTrue();
        assertThat(state.getStarInvincibilityCounter()).isEqualTo(224);
    }

    @Test
    @DisplayName("Counting down stops at zero, which ends the invincibility")
    void countingDownEndsAtZero() {
        // Prepare
        state.startStarInvincibility();

        // Execute
        for (int count = 0; count < 300; count++) {
            state.decrementStarInvincibility();
        }

        // Verify
        assertThat(state.getStarInvincibilityCounter()).isZero();
        assertThat(state.isStarInvincible()).isFalse();
    }

    @Test
    @DisplayName("A new star sets the counter back to 224")
    void aNewStarRestartsTheCounter() {
        // Prepare
        state.startStarInvincibility();
        for (int count = 0; count < 100; count++) {
            state.decrementStarInvincibility();
        }

        // Execute
        state.startStarInvincibility();

        // Verify
        assertThat(state.getStarInvincibilityCounter()).isEqualTo(224);
    }

    @Test
    @DisplayName("Ending the invincibility ends the air flip with it")
    void endingStopsTheFlip() {
        // Prepare
        state.startStarInvincibility();
        state.setAirFlipping(true);

        // Execute
        state.endStarInvincibility();

        // Verify
        assertThat(state.isStarInvincible()).isFalse();
        assertThat(state.isAirFlipping()).isFalse();
    }
}
