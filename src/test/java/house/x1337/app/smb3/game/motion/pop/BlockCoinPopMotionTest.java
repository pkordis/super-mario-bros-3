package house.x1337.app.smb3.game.motion.pop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.blockCoin;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_MOTION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The block coin's flight, worked update by update: {@code Counter++}, {@code Y += YVel}, and {@code YVel++} when
 * {@code Counter & 3 == 0}, from {@code YVel = -5} and {@code Counter = 1}. The coin is removed by the update that
 * takes {@code YVel} to 5. Tick {@code n} is the position after {@code n} updates.
 */
class BlockCoinPopMotionTest {
    @Test
    @DisplayName("The coin is removed by the 39th update, so it is drawn after updates 1 to 38")
    void theFlightLasts39Updates() {
        // Prepare
        int counter = 1;
        int yVelocity = -5;
        int updates = 0;

        // Execute
        while (yVelocity != 5) {
            counter++;
            updates++;
            if ((counter & 3) == 0) {
                yVelocity++;
            }
        }

        // Verify
        assertThat(updates).isEqualTo(39);
        assertThat(blockCoin().durationTicks()).isEqualTo(updates);
    }

    @Test
    @DisplayName("The height follows the worked Y, update by update, from the launch to the removal")
    void theHeightFollowsTheWorkedYUpdateByUpdate() {
        // Prepare
        final PopMotion motion = blockCoin();
        int counter = 1;
        int yVelocity = -5;
        int y = 0;

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(0)).as("At the launch point").isZero();
        for (int tick = 1; tick <= motion.durationTicks(); tick++) {
            counter++;
            y += yVelocity;
            if ((counter & 3) == 0) {
                yVelocity++;
            }
            assertThat(motion.verticalOffsetAt(tick)).as("Tick %d", tick).isEqualTo(-y);
        }
        assertThat(yVelocity).as("The last update removes the coin").isEqualTo(5);
    }

    @Test
    @DisplayName("It rises 5 px for 3 updates, then 4, 3, 2, 1 and 0 for 4 updates each, and falls back 1 to 4")
    void theStepsOfTheFlight() {
        // Prepare
        final PopMotion motion = blockCoin();
        final int[] expectedSteps = {
            5, 5, 5, 4, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1, 0, 0, 0, 0,
            -1, -1, -1, -1, -2, -2, -2, -2, -3, -3, -3, -3, -4, -4, -4, -4
        };

        // Execute & Verify
        assertThat(expectedSteps).hasSize(motion.durationTicks());
        for (int tick = 1; tick <= motion.durationTicks(); tick++) {
            final int step = motion.verticalOffsetAt(tick) - motion.verticalOffsetAt(tick - 1);
            assertThat(step).as("Update %d", tick).isEqualTo(expectedSteps[tick - 1]);
        }
    }

    @Test
    @DisplayName("It peaks 55 px up after updates 19 to 23 and is 15 px up when it is removed")
    void thePeakAndTheRemoval() {
        // Prepare
        final PopMotion motion = blockCoin();

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(1)).as("The first drawn frame").isEqualTo(5);
        assertThat(motion.verticalOffsetAt(18)).isEqualTo(54);
        for (int tick = 19; tick <= 23; tick++) {
            assertThat(motion.verticalOffsetAt(tick)).as("Tick %d", tick).isEqualTo(55);
        }
        assertThat(motion.verticalOffsetAt(24)).isEqualTo(54);
        assertThat(motion.verticalOffsetAt(38)).as("The last drawn frame").isEqualTo(19);
        assertThat(motion.verticalOffsetAt(39)).as("Where the caption goes").isEqualTo(15);
        assertThat(motion.verticalOffsetAt(-5)).as("Held at the start").isZero();
        assertThat(motion.verticalOffsetAt(500)).as("Held at the end").isEqualTo(15);
    }

    @Test
    @DisplayName("The spin pattern is picked by Counter >> 2 & 3, with the counter at 1 at the launch")
    void theSpinFollowsTheCounter() {
        // Prepare
        int counter = 1;

        // Execute & Verify
        for (int tick = 1; tick < BLOCK_COIN_MOTION.durationTicks(); tick++) {
            counter++;
            assertThat(BLOCK_COIN_MOTION.textureIndexAt(tick)).as("Tick %d", tick).isEqualTo(counter >> 2 & 3);
        }
    }
}
