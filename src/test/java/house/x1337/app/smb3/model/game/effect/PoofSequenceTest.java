package house.x1337.app.smb3.model.game.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.effect.PoofSequence.ONE_SHOT_TICKS;
import static org.assertj.core.api.Assertions.assertThat;

class PoofSequenceTest {
    /** Indices into the shared poof frame set, {@code frame_0} to {@code frame_3}. */
    private static final int FRAME_0 = 0;
    private static final int FRAME_1 = 1;
    private static final int FRAME_2 = 2;
    private static final int FRAME_3 = 3;

    @Test
    @DisplayName("The poof death draws frames 2, 3, 1, 0 as its timer counts down")
    void poofDeathPlaysItsOwnOrder() {
        // Prepare
        final PoofSequence sequence = PoofSequence.poofDeath();
        final List<Integer> drawn = new ArrayList<>();

        // Execute - the counter as it stands on each tick from the kill tick on
        for (int counter = sequence.startTicks(); counter >= 0; counter--) {
            if (sequence.isDrawnAt(counter)) {
                drawn.add(sequence.frameIndexFor(counter));
            }
        }

        // Verify - 30..24, 23..16, 15..8, 7..1
        assertThat(drawn).hasSize(30);
        assertThat(drawn.subList(0, 7)).containsOnly(FRAME_2);
        assertThat(drawn.subList(7, 15)).containsOnly(FRAME_3);
        assertThat(drawn.subList(15, 23)).containsOnly(FRAME_1);
        assertThat(drawn.subList(23, 30)).containsOnly(FRAME_0);
    }

    @Test
    @DisplayName("The poof death draws nothing on the kill tick, the tick after it or at timer 0")
    void poofDeathSkipsItsUndrawnTicks() {
        // Prepare
        final PoofSequence sequence = PoofSequence.poofDeath();

        // Execute & Verify
        assertThat(sequence.startTicks()).isEqualTo(32);
        assertThat(sequence.isDrawnAt(32)).as("The kill tick: the plant is still drawn").isFalse();
        assertThat(sequence.isDrawnAt(31)).as("The tick the timer is set").isFalse();
        assertThat(sequence.isDrawnAt(30)).isTrue();
        assertThat(sequence.isDrawnAt(1)).isTrue();
        assertThat(sequence.isDrawnAt(0)).as("The enemy is removed").isFalse();
    }

    @Test
    @DisplayName("The poof death flips every 2 ticks, twice as often as the switch puff")
    void poofDeathFlipsEveryTwoTicks() {
        // Prepare
        final PoofSequence sequence = PoofSequence.poofDeath();

        // Execute & Verify - bit 1 of the counter sets the flip
        for (int counter = 0; counter < 32; counter++) {
            assertThat(sequence.isFlippedVerticallyAt(counter))
                .as("Counter %d", counter)
                .isEqualTo((counter & 2) != 0);
        }
    }

    @Test
    @DisplayName("The switch puff is drawn on every tick of its countdown, as before")
    void oneShotIsAlwaysDrawn() {
        // Prepare
        final PoofSequence sequence = PoofSequence.oneShot();

        // Execute & Verify
        for (int counter = ONE_SHOT_TICKS; counter >= 0; counter--) {
            assertThat(sequence.isDrawnAt(counter)).as("Counter %d", counter).isTrue();
        }
        assertThat(sequence.frameIndexFor(ONE_SHOT_TICKS - 1)).isEqualTo(FRAME_3);
    }

    @Test
    @DisplayName("Switch-block puff plays 4 frames of 8 ticks, descending, over 32 ticks")
    void oneShotPlaysDescendingEightTickFrames() {
        // Prepare
        final PoofSequence sequence = PoofSequence.oneShot();

        // Execute - walk the counter down one tick at a time
        final List<Integer> frames = new ArrayList<>();
        for (int counter = ONE_SHOT_TICKS - 1; counter >= 0; counter--) {
            frames.add(sequence.frameIndexFor(counter));
        }

        // Verify
        assertThat(frames).hasSize(31 + 1);
        assertThat(frames.subList(0, 8)).containsOnly(3);
        assertThat(frames.subList(8, 16)).containsOnly(2);
        assertThat(frames.subList(16, 24)).containsOnly(1);
        assertThat(frames.subList(24, 32)).containsOnly(0);
    }

    @Test
    @DisplayName("Suit-change puff matches (counter & 12) >> 2 across the whole 23-tick countdown")
    void suitChangePuffMatchesTheMaskedCounterIndex() {
        // Prepare
        final PoofSequence sequence = PoofSequence.wrapping();

        // Execute & Verify - the masked index is equivalent to (counter >> 2) & 3
        for (int counter = 23; counter >= 0; counter--) {
            final int maskedIndex = (counter & 12) >> 2;
            assertThat(sequence.frameIndexFor(counter))
                .as("counter %d", counter)
                .isEqualTo(maskedIndex);
        }
    }

    @Test
    @DisplayName("Suit-change puff cycles rather than clamping, unlike the one-shot")
    void wrappingCyclesWhereOneShotClamps() {
        // Prepare
        final PoofSequence wrapping = PoofSequence.wrapping();
        final PoofSequence oneShot = PoofSequence.oneShot();

        // Execute & Verify
        // Counter 16 is step 4 for a 4-tick frame: wrapping folds back to 0, clamping would hold 3.
        assertThat(wrapping.frameIndexFor(16)).isZero();
        // Well past the one-shot's range, the clamp holds the last frame instead of wrapping.
        assertThat(oneShot.frameIndexFor(1000)).isEqualTo(PoofSequence.FRAME_COUNT - 1);
    }

    @Test
    @DisplayName("Vertical flip alternates every 4 ticks, twice per one-shot frame")
    void verticalFlipTogglesEveryFourTicks() {
        // Prepare
        final PoofSequence sequence = PoofSequence.oneShot();

        // Execute & Verify - bit 2 of the counter sets the flip
        for (int counter = 0; counter < ONE_SHOT_TICKS; counter++) {
            assertThat(sequence.isFlippedVerticallyAt(counter))
                .as("counter %d", counter)
                .isEqualTo((counter & 4) != 0);
        }
        // Both flip states occur within a single 8-tick frame.
        assertThat(sequence.frameIndexFor(24)).isEqualTo(sequence.frameIndexFor(28));
        assertThat(sequence.isFlippedVerticallyAt(24))
            .isNotEqualTo(sequence.isFlippedVerticallyAt(28));
    }

    @Test
    @DisplayName("Frame indices always stay inside the shared 4-frame set")
    void frameIndexIsAlwaysInRange() {
        // Prepare
        final List<PoofSequence> sequences = List.of(
            PoofSequence.oneShot(),
            PoofSequence.wrapping(),
            PoofSequence.poofDeath()
        );

        // Execute & Verify
        for (final PoofSequence sequence : sequences) {
            for (int counter = 0; counter <= 512; counter++) {
                assertThat(sequence.frameIndexFor(counter))
                    .as("%s at counter %d", sequence, counter)
                    .isBetween(0, PoofSequence.FRAME_COUNT - 1);
            }
        }
    }
}
