package house.x1337.app.smb3.model.game.motion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static java.lang.Math.min;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The ROM's 4.4 fixed-point object velocity (dasm smb3.asm:3558 — divide by 16 for pixels/frame),
 * and the one place that conversion is written.
 */
class FixedPointVelocityTest {
    private static final double TOLERANCE = 1e-9;

    /** {@code OBJECT_FALLRATE}, dasm prg000.asm:3362. */
    private static final int FALL_RATE = 3;

    /** {@code OBJECT_MAXFALL}. */
    private static final int TERMINAL_VELOCITY = 64;

    @Test
    @DisplayName("A velocity converts to pixels per frame by dividing by 16")
    void velocityConvertsToPixelsPerFrameByDividingBySixteen() {
        // Prepare - the Goomba's walk speed and the tail-strike launch
        final FixedPointVelocity velocity = new FixedPointVelocity(8, -80);

        // Execute & Verify
        assertThat(velocity.pixelStepX()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(velocity.pixelStepY()).isCloseTo(-5.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Gravity accumulates a fixed fall rate each frame until terminal velocity caps it")
    void gravityAccumulatesUntilTerminalVelocityCapsIt() {
        // Prepare
        FixedPointVelocity velocity = STILL;

        // Execute & Verify - every frame is exactly one fall rate faster, then pinned at the cap
        for (int frame = 1; frame <= 30; frame++) {
            velocity = velocity.falling(FALL_RATE, TERMINAL_VELOCITY);
            assertThat(velocity.y())
                .as("Frame %d", frame)
                .isEqualTo(min(frame * FALL_RATE, TERMINAL_VELOCITY));
        }
        assertThat(velocity.y()).as("Settled at OBJECT_MAXFALL").isEqualTo(TERMINAL_VELOCITY);
    }

    @Test
    @DisplayName("Gravity bounds the fall only, never the rise, so a launch keeps its full upward speed")
    void gravityBoundsTheFallButNotTheRise() {
        // Prepare - the tail-strike launch, well past any lower bound a two-sided clamp would add
        final FixedPointVelocity launched = new FixedPointVelocity(8, -80);

        // Execute
        final FixedPointVelocity afterOneFrame = launched.falling(FALL_RATE, TERMINAL_VELOCITY);

        // Verify - decays by the fall rate and is not clamped toward zero
        assertThat(afterOneFrame.y()).isEqualTo(-80 + FALL_RATE);
        assertThat(afterOneFrame.rising()).as("Still travelling upward").isTrue();
    }

    @Test
    @DisplayName("Gravity leaves the horizontal velocity alone: there is no friction on a falling object")
    void gravityLeavesTheHorizontalVelocityAlone() {
        // Prepare
        final FixedPointVelocity velocity = new FixedPointVelocity(-16, 0);

        // Execute
        final FixedPointVelocity fallen = velocity.falling(FALL_RATE, TERMINAL_VELOCITY);

        // Verify
        assertThat(fallen.x()).isEqualTo(-16);
    }

    @Test
    @DisplayName("Setting the horizontal velocity from a facing is how facing becomes direction")
    void horizontalVelocityIsDerivedFromTheFacing() {
        // Prepare
        final FixedPointVelocity falling = new FixedPointVelocity(0, 24);

        // Execute
        final FixedPointVelocity rightward = falling.directedX(true, 8);
        final FixedPointVelocity leftward = falling.directedX(false, 8);

        // Verify
        assertThat(rightward.x()).isEqualTo(8);
        assertThat(leftward.x()).isEqualTo(-8);
        assertThat(rightward.y()).as("The fall is untouched").isEqualTo(24);
    }

    @Test
    @DisplayName("An about-face reverses the horizontal travel and keeps the fall")
    void aboutFaceReversesOnlyTheHorizontalTravel() {
        // Prepare - a mushroom rolling right while falling
        final FixedPointVelocity velocity = new FixedPointVelocity(16, 12);

        // Execute
        final FixedPointVelocity reversed = velocity.negatedX();

        // Verify
        assertThat(reversed.x()).isEqualTo(-16);
        assertThat(reversed.y()).isEqualTo(12);
    }

    @Test
    @DisplayName("The direction predicates read the sign of each axis, with level counting as descending")
    void directionPredicatesReadTheSignOfEachAxis() {
        // Prepare & Execute & Verify
        assertThat(new FixedPointVelocity(8, 0).movingRight()).isTrue();
        assertThat(new FixedPointVelocity(-8, 0).movingLeft()).isTrue();
        assertThat(STILL.stationaryX()).isTrue();
        assertThat(STILL.descending()).as("Level counts as descending, as the landing test needs").isTrue();
        assertThat(STILL.rising()).isFalse();
        assertThat(new FixedPointVelocity(0, -1).rising()).isTrue();
        assertThat(new FixedPointVelocity(0, -1).descending()).isFalse();
    }

    /**
     * Pins the one numeric question this type's introduction raised. The mushroom used to convert
     * its velocity with {@code PIXELS_TO_GAME_UNITS} — a {@code float} meaning sprite-pixels to jME
     * world units, which merely happens to also be 1/16 — where this type uses a {@code double}.
     * {@code 1/16} is exactly representable and the velocities are small integers, so the two agree
     * exactly rather than approximately; asserted across the mushroom's whole range so the claim is
     * checked rather than argued. No mushroom motion test exists to catch it otherwise.
     */
    @Test
    @DisplayName("The pixel step matches the float game-units conversion the mushroom used to use")
    void pixelStepMatchesTheFloatConversionItReplaced() {
        // Prepare - the mushroom's velocity range: X is 0 or +/-16, Y spans the block bump to terminal
        // Execute & Verify
        for (int fixedPoint = -80; fixedPoint <= 80; fixedPoint++) {
            final FixedPointVelocity velocity = new FixedPointVelocity(fixedPoint, fixedPoint);
            final double viaFloat = fixedPoint * PIXELS_TO_GAME_UNITS;
            assertThat(velocity.pixelStepX())
                .as("Fixed point %d", fixedPoint)
                .isEqualTo(viaFloat);
            assertThat(velocity.pixelStepY())
                .as("Fixed point %d", fixedPoint)
                .isEqualTo(viaFloat);
        }
    }
}
