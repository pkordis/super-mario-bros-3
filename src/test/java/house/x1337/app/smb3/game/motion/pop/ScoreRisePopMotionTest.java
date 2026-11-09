package house.x1337.app.smb3.game.motion.pop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.scoreRise;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A score caption's rise, worked update by update: the score's counter goes down from 48, and the score rises when
 * the frame counter masked by the rise mask for {@code counter >> 4} is 0, the masks being 3, 1, 0 and 0.
 */
class ScoreRisePopMotionTest {
    private static final int[] RISE_COUNTER_MASKS = {3, 1, 0, 0};
    private static final int UNLIMITED_RISES = 255;

    @Test
    @DisplayName("A score is drawn on its 48 updates and removed by the 49th")
    void aScoreLasts48Updates() {
        // Execute
        final int durationTicks = scoreRise(0, UNLIMITED_RISES).durationTicks();

        // Verify
        assertThat(durationTicks).isEqualTo(48 + 1);
    }

    @Test
    @DisplayName("The digits start 8 lines below the sprite's top and rise as worked here, for any frame counter")
    void theDigitsRiseAsWorkedForAnyFrameCounter() {
        // Execute & Verify
        for (int firstUpdateFrame = 0; firstUpdateFrame < 4; firstUpdateFrame++) {
            final PopMotion motion = scoreRise(firstUpdateFrame, UNLIMITED_RISES);
            int counter = 48;
            int risen = 0;
            assertThat(motion.verticalOffsetAt(0)).isEqualTo(-8);
            for (int update = 1; update <= 48; update++) {
                counter--;
                final int frame = firstUpdateFrame + update - 1;
                if ((frame & RISE_COUNTER_MASKS[counter >> 4]) == 0) {
                    risen++;
                }
                assertThat(motion.verticalOffsetAt(update)).as("Frame counter %d, update %d", firstUpdateFrame, update)
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
