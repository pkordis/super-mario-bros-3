package house.x1337.app.smb3.model.game.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.effect.PoofSequence.ONE_SHOT_TICKS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shared poof frame policy to the two ROM routines it models.
 *
 * <p>Both index the same table, {@code .byte $47, $45, $43, $41}, which the shared sprite set stores in
 * that order ({@code frame_0} = $47 small dust … {@code frame_3} = $41 big puff):
 *
 * <ul>
 *   <li>prg007 {@code SObj_Poof} — {@code SpecialObj_Data} seeded at $20, {@code DEC} per tick, index
 *       {@code data >> 3}.</li>
 *   <li>prg029 {@code Player_SuitLost_DoPoof} — index {@code (Player_SuitLost & $0C) >> 2}.</li>
 * </ul>
 */
class PoofSequenceTest {

    @Test
    @DisplayName("Switch-block puff plays 4 frames of 8 ticks, descending, over 32 ticks")
    void oneShotPlaysDescendingEightTickFrames() {
        // Prepare
        final PoofSequence sequence = PoofSequence.oneShot();

        // Execute - walk the counter down exactly as SObj_Poof's DEC does
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
    @DisplayName("Suit-change puff matches (counter & $0C) >> 2 across the whole $17-tick countdown")
    void wrappingMatchesRomMaskedIndex() {
        // Prepare
        final PoofSequence sequence = PoofSequence.wrapping();

        // Execute & Verify - the ROM's masked index is equivalent to (counter >> 2) & 3
        for (int counter = 0x17; counter >= 0; counter--) {
            final int romIndex = (counter & 0x0C) >> 2;
            assertThat(sequence.frameIndexFor(counter))
                .as("counter %d", counter)
                .isEqualTo(romIndex);
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

        // Execute & Verify - the ROM keeps bit 2 of Level_NoStopCnt (LSR x3 / ROR / AND #$80)
        for (int counter = 0; counter < ONE_SHOT_TICKS; counter++) {
            assertThat(sequence.isFlippedVerticallyAt(counter))
                .as("counter %d", counter)
                .isEqualTo((counter & 0x04) != 0);
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
        final List<PoofSequence> sequences = List.of(PoofSequence.oneShot(), PoofSequence.wrapping());

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
