package house.x1337.app.smb3.game.motion.pop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.blockCoin;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_MOTION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The block coin's flight, worked update by update as {@code CoinPUp_UpdateAndDraw} runs it (dasm
 * prg007.asm:2786-2855): {@code Counter++}, {@code Y += YVel}, and {@code YVel++} when {@code Counter & 3 == 0}, from
 * {@code YVel = -5} and {@code Counter = 1} (prg000.asm:779-803). The coin is removed by the update that takes
 * {@code YVel} to 5.
 */
class BlockCoinPopMotionTest {
    @Test
    @DisplayName("The coin is removed by the 39th update, so it is drawn for 38 ticks")
    void theFlightLasts38Ticks() {
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
        assertThat(blockCoin().durationTicks()).as("Tick 0 is the first update").isEqualTo(updates - 1);
    }

    @Test
    @DisplayName("The height follows the ROM's Y, update by update, from the launch to the removal")
    void theHeightMatchesTheRom() {
        // Prepare
        final PopMotion motion = blockCoin();
        int counter = 1;
        int yVelocity = -5;
        int y = 0;

        // Execute & Verify
        for (int tick = 0; tick <= motion.durationTicks(); tick++) {
            counter++;
            y += yVelocity;
            if ((counter & 3) == 0) {
                yVelocity++;
            }
            assertThat(motion.verticalOffsetAt(tick)).as("Tick %d", tick)
                .isEqualTo(BlockCoinPopMotion.LAUNCH_HEIGHT - y);
        }
        assertThat(yVelocity).as("The last update removes the coin").isEqualTo(5);
    }

    @Test
    @DisplayName("It rises 5 px for 3 ticks, then 4, 3, 2, 1 and 0 for 4 ticks each, and falls back 1 to 4")
    void theStepsOfTheFlight() {
        // Prepare
        final PopMotion motion = blockCoin();
        final int[] expectedSteps = {
            5, 5, 4, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1, 0, 0, 0, 0,
            -1, -1, -1, -1, -2, -2, -2, -2, -3, -3, -3, -3, -4, -4, -4, -4
        };

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(0)).as("The first update's 5 px")
            .isEqualTo(BlockCoinPopMotion.LAUNCH_HEIGHT + 5);
        for (int tick = 1; tick <= motion.durationTicks(); tick++) {
            final int step = motion.verticalOffsetAt(tick) - motion.verticalOffsetAt(tick - 1);
            assertThat(step).as("Tick %d", tick).isEqualTo(expectedSteps[tick - 1]);
        }
    }

    @Test
    @DisplayName("The coin starts 9 px above the block's top: 24 px above the head, 14 px into the block, 1 line down")
    void theLaunchHeight() {
        // Execute
        final int launchHeight = BlockCoinPopMotion.LAUNCH_HEIGHT;

        // Verify
        assertThat(launchHeight).isEqualTo(9);
        assertThat(blockCoin().verticalOffsetAt(0)).as("Drawn after its first update").isEqualTo(14);
    }

    @Test
    @DisplayName("It peaks 64 px above the block's top for ticks 18 to 22 and is 24 px up when it is removed")
    void thePeakAndTheRemoval() {
        // Prepare
        final PopMotion motion = blockCoin();

        // Execute & Verify
        assertThat(motion.verticalOffsetAt(17)).isEqualTo(63);
        for (int tick = 18; tick <= 22; tick++) {
            assertThat(motion.verticalOffsetAt(tick)).as("Tick %d", tick).isEqualTo(64);
        }
        assertThat(motion.verticalOffsetAt(23)).isEqualTo(63);
        assertThat(motion.verticalOffsetAt(37)).as("The last drawn tick").isEqualTo(28);
        assertThat(motion.verticalOffsetAt(38)).as("Where the caption goes").isEqualTo(24);
        assertThat(motion.verticalOffsetAt(-5)).as("Held at the start").isEqualTo(14);
        assertThat(motion.verticalOffsetAt(500)).as("Held at the end").isEqualTo(24);
    }

    @Test
    @DisplayName("The spin pattern is CoinPUp_Patterns[Counter >> 2 & 3], with the counter at 2 on tick 0")
    void theSpinFollowsTheCounter() {
        // Prepare
        int counter = 1;

        // Execute & Verify
        for (int tick = 0; tick < BLOCK_COIN_MOTION.durationTicks(); tick++) {
            counter++;
            assertThat(BLOCK_COIN_MOTION.textureIndexAt(tick)).as("Tick %d", tick).isEqualTo(counter >> 2 & 3);
        }
    }
}
