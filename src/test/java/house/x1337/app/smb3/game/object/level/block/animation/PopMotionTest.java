package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.game.motion.pop.PopMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.deceleratingRise;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.parabolic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @DisplayName("score rise reproduces the traced table pixel-for-pixel")
    void scoreRiseMatchesTracedTable() {
        assertEquals(TRACED_SCORE_OFFSETS.length, SCORE_MOTION.durationTicks());
        for (int tick = 0; tick < TRACED_SCORE_OFFSETS.length; tick++) {
            assertEquals(TRACED_SCORE_OFFSETS[tick], SCORE_MOTION.verticalOffsetAt(tick), "tick " + tick);
        }
    }

    @Test
    @DisplayName("coin spin reproduces the traced texture sequence exactly")
    void coinSpinMatchesTracedSequence() {
        for (int tick = 0; tick < TRACED_COIN_TEXTURES.length; tick++) {
            assertEquals(TRACED_COIN_TEXTURES[tick], COIN_MOTION.textureIndexAt(tick), "tick " + tick);
        }
    }

    @Test
    @DisplayName("coin arc runs for the traced number of ticks")
    void coinArcMatchesTracedDuration() {
        assertEquals(TRACED_COIN_OFFSETS.length, COIN_MOTION.durationTicks());
    }

    @Test
    @DisplayName("coin arc matches the traced table exactly at spawn, apex and removal")
    void coinArcMatchesTracedLandmarks() {
        final int lastTick = TRACED_COIN_OFFSETS.length - 1;

        int apex = 0;
        int apexTick = 0;
        for (int tick = 0; tick < TRACED_COIN_OFFSETS.length; tick++) {
            if (COIN_MOTION.verticalOffsetAt(tick) > apex) {
                apex = COIN_MOTION.verticalOffsetAt(tick);
                apexTick = tick;
            }
        }

        assertEquals(TRACED_COIN_OFFSETS[0], COIN_MOTION.verticalOffsetAt(0));
        assertEquals(62, apex);
        assertTrue(apexTick >= 18 && apexTick <= 22, "apex reached at tick " + apexTick);
        assertEquals(TRACED_COIN_OFFSETS[lastTick], COIN_MOTION.verticalOffsetAt(lastTick));
    }

    @Test
    @DisplayName("coin arc stays within tolerance of the traced table at every tick")
    void coinArcTracksTracedTableWithinTolerance() {
        for (int tick = 0; tick < TRACED_COIN_OFFSETS.length; tick++) {
            final int deviation = Math.abs(COIN_MOTION.verticalOffsetAt(tick) - TRACED_COIN_OFFSETS[tick]);
            assertTrue(deviation <= COIN_TOLERANCE, "tick " + tick + " deviates by " + deviation + " px");
        }
    }

    @Test
    @DisplayName("offsets clamp outside the animation lifetime")
    void offsetsClampOutsideLifetime() {
        assertEquals(SCORE_MOTION.verticalOffsetAt(0), SCORE_MOTION.verticalOffsetAt(-5));
        assertEquals(SCORE_MOTION.verticalOffsetAt(48), SCORE_MOTION.verticalOffsetAt(500));
        assertEquals(COIN_MOTION.verticalOffsetAt(0), COIN_MOTION.verticalOffsetAt(-5));
        assertEquals(COIN_MOTION.verticalOffsetAt(38), COIN_MOTION.verticalOffsetAt(500));
    }
}
