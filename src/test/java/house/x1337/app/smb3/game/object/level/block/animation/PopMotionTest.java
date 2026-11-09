package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.game.motion.pop.PopMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.scoreRise;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_ABOVE_PROBE_PIXELS;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_MOTION;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.SPRITE_LINE_DELAY_PIXELS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The block coin and its 100 against {@code captures/input/smb3.avi}: 1-1, raccoon Mario jumping into the low ?
 * block, whose top is screen line 113.
 */
class PopMotionTest {
    /**
     * How far into the block the head probe was at the hit. The coin's first frame puts it there, and it fits the
     * head rising 3 px a frame: 13 to 15 px.
     */
    private static final int CAPTURED_HEAD_PROBE_DEPTH = 14;

    /** Where the coin's sprite top starts, above the block's top. */
    private static final int LAUNCH_ABOVE_BLOCK_TOP =
        BLOCK_COIN_ABOVE_PROBE_PIXELS - CAPTURED_HEAD_PROBE_DEPTH - SPRITE_LINE_DELAY_PIXELS;

    /**
     * Frames 62 to 99: the coin sprite's top above the block's top. The coin's first opaque row is one below its
     * sprite's top, so each value is {@code 114 -} the coin's first opaque line. Frame 100 has no coin.
     */
    private static final int[] CAPTURED_COIN_OFFSETS = {
        14, 19, 24, 28, 32, 36, 40, 43, 46, 49,
        52, 54, 56, 58, 60, 61, 62, 63, 64, 64,
        64, 64, 64, 63, 62, 61, 60, 58, 56, 54,
        52, 49, 46, 43, 40, 36, 32, 28
    };

    /**
     * The spin frames on frames 62 to 99: full face, narrow (dark edge left), edge, narrow (dark edge right).
     * {@link #COVERED} marks frames 64 to 71, where the bumped block covers the coin.
     */
    private static final int[] CAPTURED_COIN_TEXTURES = {
        0, 0, -1, -1, -1, -1, -1, -1, -1, -1,
        3, 3, 3, 3, 0, 0, 0, 0, 1, 1,
        1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
        0, 0, 0, 0, 1, 1, 1, 1
    };
    private static final int COVERED = -1;

    /**
     * Frames 101 to 148: the first line of the 100's digits. Frame 149 has none. The coin was removed with its
     * sprite's top at line 88, so the score sprite's top is line 89.
     */
    private static final int[] CAPTURED_CAPTION_DIGITS_LINES = {
        96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81,
        80, 80, 79, 79, 78, 78, 77, 77, 76, 76, 75, 75, 74, 74, 73, 73,
        72, 72, 72, 72, 71, 71, 71, 71, 70, 70, 70, 70, 69, 69, 69, 69
    };
    private static final int CAPTURED_SCORE_SPRITE_TOP_LINE = 89;

    /**
     * The global frame counter on the 100's first update, modulo 4. Its rises on every other frame fall on its 17th
     * update and those every 4th frame on its 33rd, so it was a multiple of 4.
     */
    private static final int CAPTURED_CAPTION_FIRST_UPDATE_FRAME = 0;
    private static final int UNLIMITED_RISES = 255;

    @Test
    @DisplayName("The block coin is drawn after updates 1 to 38, as many frames as in the capture")
    void coinFlightMatchesCapturedDuration() {
        // Execute & Verify
        assertThat(BLOCK_COIN_MOTION.durationTicks() - 1).isEqualTo(CAPTURED_COIN_OFFSETS.length);
    }

    @Test
    @DisplayName("The block coin's height matches the capture at every frame")
    void coinFlightMatchesCapture() {
        // Execute & Verify
        for (int frame = 0; frame < CAPTURED_COIN_OFFSETS.length; frame++) {
            assertThat(LAUNCH_ABOVE_BLOCK_TOP + BLOCK_COIN_MOTION.verticalOffsetAt(frame + 1)).as("Frame %d", frame)
                .isEqualTo(CAPTURED_COIN_OFFSETS[frame]);
        }
    }

    @Test
    @DisplayName("The block coin's spin matches the capture wherever the coin is in view")
    void coinSpinMatchesCapture() {
        // Execute & Verify
        assertThat(CAPTURED_COIN_TEXTURES).hasSameSizeAs(CAPTURED_COIN_OFFSETS);
        for (int frame = 0; frame < CAPTURED_COIN_TEXTURES.length; frame++) {
            if (CAPTURED_COIN_TEXTURES[frame] != COVERED) {
                assertThat(BLOCK_COIN_MOTION.textureIndexAt(frame + 1)).as("Frame %d", frame)
                    .isEqualTo(CAPTURED_COIN_TEXTURES[frame]);
            }
        }
    }

    @Test
    @DisplayName("The 100's digits match the capture on all 48 frames it is drawn")
    void captionMatchesCapture() {
        // Prepare
        final PopMotion motion = scoreRise(CAPTURED_CAPTION_FIRST_UPDATE_FRAME, UNLIMITED_RISES);

        // Execute & Verify
        assertThat(motion.durationTicks() - 1).isEqualTo(CAPTURED_CAPTION_DIGITS_LINES.length);
        for (int frame = 0; frame < CAPTURED_CAPTION_DIGITS_LINES.length; frame++) {
            final int digitsLine = CAPTURED_SCORE_SPRITE_TOP_LINE - motion.verticalOffsetAt(frame + 1);
            assertThat(digitsLine).as("Frame %d", frame).isEqualTo(CAPTURED_CAPTION_DIGITS_LINES[frame]);
        }
    }
}
