package house.x1337.app.smb3.model.game.player.level.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.NORMAL_TRANSITION_TICKS;
import static house.x1337.app.smb3.model.game.player.level.asset.SizeChangingAnimatorAssets.frameIndexFor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the direction of the shared size-change animation. One {@code Player_Grow} counter and one frame
 * table drive both the Shrunk→Normal grow and the Normal→Shrunk shrink; the ROM tells them apart by
 * reversing the table index when the suit is small (prg029 @ PRG029_D22E). The regression this guards:
 * a shrink that played the grow sequence and only snapped small once the counter hit zero, so the
 * player appeared to grow big and then pop back to small.
 */
class SizeChangingAnimatorAssetsTest {
    private static final int SHRUNK = 0;
    private static final int NORMAL = 2;

    @Test
    @DisplayName("The grow settles on the normal frame as its counter drains to zero")
    void growEndsOnNormal() {
        // The forward read: X = Player_Grow >> 2. Prepare / Execute at the end of the countdown
        final int lastGrowFrame = frameIndexFor(0, false);

        // Verify
        assertThat(lastGrowFrame).as("a grow lands big").isEqualTo(NORMAL);
    }

    @Test
    @DisplayName("The shrink does not settle on the normal frame — it trends the other way")
    void shrinkDoesNotEndOnNormal() {
        // The reversed read: X = $0B - (Player_Grow >> 2). The last transition frame must not be the
        // normal frame, which was the bug — the shrink previously replayed the grow and ended big.
        // Prepare / Execute at the end of the countdown
        final int lastShrinkFrame = frameIndexFor(0, true);

        // Verify
        assertThat(lastShrinkFrame).as("a shrink must not land big").isNotEqualTo(NORMAL);
    }

    @Test
    @DisplayName("Across the whole shrink the normal frame shows early and the shrunk frame shows late")
    void shrinkProgressesFromNormalTowardShrunk() {
        // A coarse direction check over the real counter range: the first half of the countdown (high
        // counter = start of the transition) must show the normal frame at least once, and the second
        // half (low counter = end) must show the shrunk frame at least once. Prepare
        boolean normalSeenEarly = false;
        boolean shrunkSeenLate = false;
        final int half = NORMAL_TRANSITION_TICKS / 2;

        // Execute - walk the counter from full ($2f) down to zero, as the game does
        for (int counter = NORMAL_TRANSITION_TICKS; counter >= 0; counter--) {
            final int frame = frameIndexFor(counter, true);
            if (counter > half && frame == NORMAL) {
                normalSeenEarly = true;
            }
            if (counter <= half && frame == SHRUNK) {
                shrunkSeenLate = true;
            }
        }

        // Verify
        assertThat(normalSeenEarly).as("the shrink starts off looking big").isTrue();
        assertThat(shrunkSeenLate).as("and ends up looking small").isTrue();
    }
}
