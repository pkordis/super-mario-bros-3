package house.x1337.app.smb3.game.object.level.enemy.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class WalkAnimationClockTest {

    @ParameterizedTest(name = "rate {0}")
    @CsvSource({
        "256, 4, 8, 12, 16",
        "128, 8, 16, 24, 32",
        "64, 16, 32, 48, 64"
    })
    @DisplayName("The walk frame changes every 4 steps, a step taking 256 / rate ticks")
    void theFrameChangesEveryFourSteps(
        final int rate,
        final int first,
        final int second,
        final int third,
        final int fourth
    ) {
        // Prepare
        final WalkAnimationClock clock = new WalkAnimationClock();

        // Execute
        final List<Integer> changes = new ArrayList<>();
        for (int tick = 1; tick <= fourth; tick++) {
            if (clock.advance(rate)) {
                changes.add(tick);
            }
        }

        // Verify
        assertThat(changes).containsExactly(first, second, third, fourth);
    }

    @Test
    @DisplayName("A rate just short of a full step loses a step now and then")
    void aNearlyFullRateSkipsNowAndThen() {
        // Prepare
        final WalkAnimationClock clock = new WalkAnimationClock();

        // Execute
        for (int tick = 0; tick < 256; tick++) {
            clock.advance(255);
        }

        // Verify
        assertThat(clock.getSteps()).isEqualTo(255);
    }

    @Test
    @DisplayName("The step count wraps after 256 steps")
    void theStepsWrap() {
        // Prepare
        final WalkAnimationClock clock = new WalkAnimationClock();

        // Execute
        for (int tick = 0; tick < 257; tick++) {
            clock.advance(256);
        }

        // Verify
        assertThat(clock.getSteps()).isEqualTo(1);
    }
}
