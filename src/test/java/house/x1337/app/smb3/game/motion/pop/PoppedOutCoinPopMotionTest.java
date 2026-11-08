package house.x1337.app.smb3.game.motion.pop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.poppedOutCoin;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The popped-out coin's flight, worked frame by frame as {@code SObj_ApplyXYVelsWithGravity} runs it (dasm
 * prg007.asm:3903-3920): {@code YVel} starts at {@code -$28}, is added in 4.4 fixed point, then grows by 2, and the
 * coin is removed once it reaches {@code $20} (prg007.asm:3506-3516).
 */
class PoppedOutCoinPopMotionTest {
    @Test
    @DisplayName("The coin lasts 36 ticks: the velocity reaches $20 on the 36th")
    void theFlightLasts36Ticks() {
        // Execute
        final int durationTicks = poppedOutCoin().durationTicks();

        // Verify
        assertThat(durationTicks).isEqualTo(36);
        assertThat(-0x28 + 2 * 35).as("Still below $20 a tick earlier").isLessThan(0x20);
        assertThat(-0x28 + 2 * 36).isGreaterThanOrEqualTo(0x20);
    }

    @Test
    @DisplayName("The height follows the ROM's 4.4 position, tick by tick, from the launch to the end")
    void theHeightMatchesTheRom() {
        // Prepare
        final PopMotion motion = poppedOutCoin();
        int yVelocity = -0x28;
        int y = 0;

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(0)).isZero();
        for (int tick = 1; tick <= 36; tick++) {
            y += yVelocity;
            yVelocity += 2;
            assertThat(motion.verticalOffsetAt(tick)).as("Tick %d", tick).isEqualTo(-Math.floorDiv(y, 16));
        }
    }

    @Test
    @DisplayName("It rises 3, then 2 px, peaks 27 px up and ends 12 px above its start")
    void theShapeOfTheFlight() {
        // Prepare
        final PopMotion motion = poppedOutCoin();

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(1)).isEqualTo(3);
        assertThat(motion.verticalOffsetAt(2)).isEqualTo(5);
        assertThat(motion.verticalOffsetAt(20)).isEqualTo(27);
        assertThat(motion.verticalOffsetAt(36)).isEqualTo(12);
        assertThat(motion.verticalOffsetAt(50)).as("Held at the end").isEqualTo(12);
    }
}
