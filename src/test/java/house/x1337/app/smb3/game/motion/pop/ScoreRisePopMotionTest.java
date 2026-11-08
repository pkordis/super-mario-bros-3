package house.x1337.app.smb3.game.motion.pop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.scoreRise;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A score caption's rise, worked update by update as {@code Score_GiveAndDraw} runs it (dasm prg007.asm:2191-2267):
 * {@code Scores_Counter} goes down from $30, and the score rises when
 * {@code Counter_1 & Score_RiseCounterMask[counter >> 4]} is 0, the masks being 3, 1, 0 and 0.
 */
class ScoreRisePopMotionTest {
    private static final int[] RISE_COUNTER_MASKS = {0x03, 0x01, 0x00, 0x00};
    private static final int UNLIMITED_RISES = 255;

    @Test
    @DisplayName("A score is drawn on its 48 updates and removed by the 49th")
    void aScoreLasts48Updates() {
        // Execute
        final int durationTicks = scoreRise(0, UNLIMITED_RISES).durationTicks();

        // Verify
        assertThat(durationTicks).isEqualTo(0x30 + 1);
    }

    @Test
    @DisplayName("The digits start 8 lines below the sprite's top and rise as the ROM lifts them, for any Counter_1")
    void theRiseMatchesTheRom() {
        // Execute & Verify
        for (int firstUpdateFrame = 0; firstUpdateFrame < 4; firstUpdateFrame++) {
            final PopMotion motion = scoreRise(firstUpdateFrame, UNLIMITED_RISES);
            int counter = 0x30;
            int risen = 0;
            assertThat(motion.verticalOffsetAt(0)).isEqualTo(-8);
            for (int update = 1; update <= 0x30; update++) {
                counter--;
                final int frame = firstUpdateFrame + update - 1;
                if ((frame & RISE_COUNTER_MASKS[counter >> 4]) == 0) {
                    risen++;
                }
                assertThat(motion.verticalOffsetAt(update)).as("Counter_1 %d, update %d", firstUpdateFrame, update)
                    .isEqualTo(risen - 8);
            }
        }
    }

    @Test
    @DisplayName("It rises 16 px in the first 16 updates, then 8 and 4, so 28 in all")
    void theRiseSlowsDown() {
        // Prepare
        final PopMotion motion = scoreRise(0, UNLIMITED_RISES);

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(16)).isEqualTo(16 - 8);
        assertThat(motion.verticalOffsetAt(32)).isEqualTo(24 - 8);
        assertThat(motion.verticalOffsetAt(48)).isEqualTo(28 - 8);
        assertThat(motion.verticalOffsetAt(500)).as("Held at the end").isEqualTo(28 - 8);
    }

    @Test
    @DisplayName("It stops rising once it has risen as far as it may")
    void theRiseStops() {
        // Prepare
        final PopMotion motion = scoreRise(0, 2);

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(1)).isEqualTo(1 - 8);
        assertThat(motion.verticalOffsetAt(2)).isEqualTo(2 - 8);
        assertThat(motion.verticalOffsetAt(48)).isEqualTo(2 - 8);
    }
}
