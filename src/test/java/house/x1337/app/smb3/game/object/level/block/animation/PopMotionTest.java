package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.game.motion.pop.PopMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.deceleratingRise;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_MOTION;
import static org.assertj.core.api.Assertions.assertThat;

class PopMotionTest {

    /**
     * The block coin in {@code captures/input/smb3.avi}, frames 62 to 99: the top of its sprite above the block's
     * top. The block's top is screen line 113 and the coin's first opaque row is one below its sprite's top, so each
     * value is {@code 114 -} the coin's first opaque line. Frame 100 has no coin; the 100 caption shows from 101.
     */
    private static final int[] CAPTURED_COIN_OFFSETS = {
        14, 19, 24, 28, 32, 36, 40, 43, 46, 49,
        52, 54, 56, 58, 60, 61, 62, 63, 64, 64,
        64, 64, 64, 63, 62, 61, 60, 58, 56, 54,
        52, 49, 46, 43, 40, 36, 32, 28
    };

    /**
     * The spin frames in the same capture: full face, narrow (dark edge left), edge, narrow (dark edge right).
     * {@link #COVERED} marks frames 64 to 71, where the bumped block covers the coin.
     */
    private static final int[] CAPTURED_COIN_TEXTURES = {
        0, 0, -1, -1, -1, -1, -1, -1, -1, -1,
        3, 3, 3, 3, 0, 0, 0, 0, 1, 1,
        1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
        0, 0, 0, 0, 1, 1, 1, 1
    };
    private static final int COVERED = -1;

    /** Offsets baked into {@code ScorePopupAnimation}, measured from the corrected -4 spawn. */
    private static final int[] TRACED_SCORE_OFFSETS = {
        -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
        12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18, 19, 19,
        20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 23, 23, 23, 23
    };

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
    @DisplayName("The block coin is drawn for as many frames as in the capture")
    void coinFlightMatchesCapturedDuration() {
        // Execute & Verify
        assertThat(BLOCK_COIN_MOTION.durationTicks()).isEqualTo(CAPTURED_COIN_OFFSETS.length);
    }

    @Test
    @DisplayName("The block coin's height matches the capture at every frame")
    void coinFlightMatchesCapture() {
        // Execute & Verify
        for (int tick = 0; tick < CAPTURED_COIN_OFFSETS.length; tick++) {
            assertThat(BLOCK_COIN_MOTION.verticalOffsetAt(tick)).as("Tick " + tick)
                .isEqualTo(CAPTURED_COIN_OFFSETS[tick]);
        }
    }

    @Test
    @DisplayName("The block coin's spin matches the capture wherever the coin is in view")
    void coinSpinMatchesCapture() {
        // Execute & Verify
        assertThat(CAPTURED_COIN_TEXTURES).hasSameSizeAs(CAPTURED_COIN_OFFSETS);
        for (int tick = 0; tick < CAPTURED_COIN_TEXTURES.length; tick++) {
            if (CAPTURED_COIN_TEXTURES[tick] != COVERED) {
                assertThat(BLOCK_COIN_MOTION.textureIndexAt(tick)).as("Tick " + tick)
                    .isEqualTo(CAPTURED_COIN_TEXTURES[tick]);
            }
        }
    }

    @Test
    @DisplayName("Offsets clamp outside the animation lifetime")
    void offsetsClampOutsideLifetime() {
        // Execute & Verify
        assertThat(SCORE_MOTION.verticalOffsetAt(-5)).isEqualTo(SCORE_MOTION.verticalOffsetAt(0));
        assertThat(SCORE_MOTION.verticalOffsetAt(500)).isEqualTo(SCORE_MOTION.verticalOffsetAt(48));
        assertThat(BLOCK_COIN_MOTION.verticalOffsetAt(-5)).isEqualTo(BLOCK_COIN_MOTION.verticalOffsetAt(0));
        assertThat(BLOCK_COIN_MOTION.verticalOffsetAt(500)).isEqualTo(BLOCK_COIN_MOTION.verticalOffsetAt(38));
    }
}
