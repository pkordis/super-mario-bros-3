package house.x1337.app.smb3.model.game.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.HURT_INVINCIBILITY_TICKS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the post-hit flicker's timing.
 *
 * <p>Each frame the player is drawn as follows:
 *
 * <ol>
 *   <li>read the invincibility counter; if it is zero, the player is not flashing and is drawn normally;</li>
 *   <li>otherwise decrement the counter;</li>
 *   <li>mask the value read <em>before</em> the decrement with {@code 2};</li>
 *   <li>bit clear: draw the player; bit set: draw no player sprite at all this frame.</li>
 * </ol>
 *
 * <p>Two consequences are easy to get wrong and are what these tests exist to hold: the mask is
 * {@code 2} and not {@code 1}, so the period is four ticks (two drawn, two skipped) rather than
 * alternating every tick; and it is applied to the pre-decrement value, which sets the phase the
 * sequence opens on.
 */
class HurtInvincibilityFlickerTest {
    @Test
    @DisplayName("A hit grants 113 ticks of invincibility")
    void invincibilityLastsOneHundredThirteenTicks() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setHurtInvincibilityCounter(HURT_INVINCIBILITY_TICKS);

        // Execute - run it dry
        int ticks = 0;
        while (runtimeState.isHurtInvincible()) {
            runtimeState.decrementHurtInvincibility();
            ticks++;
        }

        // Verify
        assertThat(HURT_INVINCIBILITY_TICKS).isEqualTo(113);
        assertThat(ticks).isEqualTo(113);
        assertThat(runtimeState.isSpriteDrawnThisTick())
            .as("once it has run out the player is solid again")
            .isTrue();
    }

    @Test
    @DisplayName("The sprite is drawn for two ticks and skipped for two, opening on a drawn pair")
    void flickerRunsTwoOnTwoOff() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setHurtInvincibilityCounter(HURT_INVINCIBILITY_TICKS);

        // Execute - sample the first three periods, in the engine's order: decide visibility, then tick
        final List<Boolean> drawn = new ArrayList<>();
        for (int tick = 0; tick < 12; tick++) {
            drawn.add(runtimeState.isSpriteDrawnThisTick());
            runtimeState.decrementHurtInvincibility();
        }

        // Verify - 113 has bit 1 clear, so the very first frame after the poof shows the player
        assertThat(drawn).containsExactly(
            true, true, false, false,
            true, true, false, false,
            true, true, false, false
        );
    }

    @Test
    @DisplayName("Every drawn tick has bit 1 of the counter clear, and every skipped tick has it set")
    void visibilityIsBitOneOfTheCounter() {
        // Prepare & Execute & Verify - the whole run, against the mask directly
        for (int counter = HURT_INVINCIBILITY_TICKS; counter > 0; counter--) {
            final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
            runtimeState.setHurtInvincibilityCounter(counter);
            assertThat(runtimeState.isSpriteDrawnThisTick())
                .as("counter %d (0x%02x)", counter, counter)
                .isEqualTo((counter & 2) == 0);
        }
    }

    @Test
    @DisplayName("A player who was never hit is always drawn")
    void notFlashingIsAlwaysDrawn() {
        // The zero case is the first check, made before the mask, and it matters: 0 & 2 is also
        // clear, so an implementation that skipped the guard would happen to work — until the counter's
        // resting value ever changed.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();

        // Verify
        assertThat(runtimeState.isHurtInvincible()).isFalse();
        assertThat(runtimeState.isSpriteDrawnThisTick()).isTrue();
    }

    @Test
    @DisplayName("The timer never runs past zero")
    void theTimerFloorsAtZero() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setHurtInvincibilityCounter(1);

        // Execute
        runtimeState.decrementHurtInvincibility();
        runtimeState.decrementHurtInvincibility();

        // Verify
        assertThat(runtimeState.getHurtInvincibilityCounter()).isZero();
    }
}
