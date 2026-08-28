package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.game.motion.pop.PopMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.deceleratingRise;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.parabolic;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the derived {@link PopMotion} functions against the hand-traced frame tables they
 * replaced, so the animations keep rendering at the offsets the project has always used.
 */
class PopMotionTest {

    /** Offsets previously baked into {@code CoinPopAnimation.VERTICAL_OFFSETS}. */
    private static final int[] TRACED_COIN_OFFSETS = {
        11, 16, 20, 25, 29, 34, 38, 41, 44, 47,
        50, 52, 54, 56,
        57, 58, 59, 60,
        62, 62, 62, 62, 62,
        61, 60, 59, 58, 56,
        54, 52, 49, 46,
        43, 40, 38, 34, 30, 26
    };

    /** Texture indices previously baked into {@code CoinPopAnimation.FRAME_SEQUENCE}. */
    private static final int[] TRACED_COIN_TEXTURES = {
        0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3,
        0, 0, 0, 0,
        1, 1, 1, 1, 2,
        2, 2, 2, 3, 3,
        3, 3, 0, 0,
        0, 0, 1, 1, 1, 1
    };

    /** Offsets baked into {@code ScorePopupAnimation}, measured from the corrected -4 spawn. */
    private static final int[] TRACED_SCORE_OFFSETS = {
        -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
        12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18, 19, 19,
        20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 23, 23, 23, 23
    };

    /**
     * Largest deviation the calibrated parabola may show against the traced coin table.
     * A floored parabola cannot do better: an exhaustive search over rise speed, gravity and
     * sub-pixel precision found no parameter set with a maximum deviation below 2 px.
     */
    private static final int COIN_TOLERANCE = 2;

    private static final PopMotion COIN_MOTION = parabolic(11, 647, 32, 128, 38).spinning(4, 4, 2);
    private static final PopMotion SCORE_MOTION = deceleratingRise(16, 3);

    @Test
    @DisplayName("Score rise reproduces the traced table pixel-for-pixel")
    void scoreRiseMatchesTracedTable() {
        // Execute & Verify
        assertThat(SCORE_MOTION.durationTicks()).isEqualTo(TRACED_SCORE_OFFSETS.length);
        for (int tick = 0; tick < TRACED_SCORE_OFFSETS.length; tick++) {
            assertThat(SCORE_MOTION.verticalOffsetAt(tick)).as("Tick " + tick).isEqualTo(TRACED_SCORE_OFFSETS[tick]);
        }
    }

    @Test
    @DisplayName("Coin spin reproduces the traced texture sequence exactly")
    void coinSpinMatchesTracedSequence() {
        // Execute & Verify
        for (int tick = 0; tick < TRACED_COIN_TEXTURES.length; tick++) {
            assertThat(COIN_MOTION.textureIndexAt(tick)).as("Tick " + tick).isEqualTo(TRACED_COIN_TEXTURES[tick]);
        }
    }

    @Test
    @DisplayName("Coin arc runs for the traced number of ticks")
    void coinArcMatchesTracedDuration() {
        // Execute & Verify
        assertThat(COIN_MOTION.durationTicks()).isEqualTo(TRACED_COIN_OFFSETS.length);
    }

    @Test
    @DisplayName("Coin arc matches the traced table exactly at spawn, apex and removal")
    void coinArcMatchesTracedLandmarks() {
        // Prepare
        final int lastTick = TRACED_COIN_OFFSETS.length - 1;

        // Execute
        int apex = 0;
        int apexTick = 0;
        for (int tick = 0; tick < TRACED_COIN_OFFSETS.length; tick++) {
            if (COIN_MOTION.verticalOffsetAt(tick) > apex) {
                apex = COIN_MOTION.verticalOffsetAt(tick);
                apexTick = tick;
            }
        }

        // Verify
        assertThat(COIN_MOTION.verticalOffsetAt(0)).isEqualTo(TRACED_COIN_OFFSETS[0]);
        assertThat(apex).isEqualTo(62);
        assertThat(apexTick).as("Apex reached at tick " + apexTick).isBetween(18, 22);
        assertThat(COIN_MOTION.verticalOffsetAt(lastTick)).isEqualTo(TRACED_COIN_OFFSETS[lastTick]);
    }

    @Test
    @DisplayName("Coin arc stays within tolerance of the traced table at every tick")
    void coinArcTracksTracedTableWithinTolerance() {
        // Execute & Verify
        for (int tick = 0; tick < TRACED_COIN_OFFSETS.length; tick++) {
            final int deviation = Math.abs(COIN_MOTION.verticalOffsetAt(tick) - TRACED_COIN_OFFSETS[tick]);
            assertThat(deviation).as("Tick " + tick + " deviates by " + deviation + " px")
                .isLessThanOrEqualTo(COIN_TOLERANCE);
        }
    }

    @Test
    @DisplayName("Offsets clamp outside the animation lifetime")
    void offsetsClampOutsideLifetime() {
        // Execute & Verify
        assertThat(SCORE_MOTION.verticalOffsetAt(-5)).isEqualTo(SCORE_MOTION.verticalOffsetAt(0));
        assertThat(SCORE_MOTION.verticalOffsetAt(500)).isEqualTo(SCORE_MOTION.verticalOffsetAt(48));
        assertThat(COIN_MOTION.verticalOffsetAt(-5)).isEqualTo(COIN_MOTION.verticalOffsetAt(0));
        assertThat(COIN_MOTION.verticalOffsetAt(500)).isEqualTo(COIN_MOTION.verticalOffsetAt(38));
    }
}
