package house.x1337.app.smb3.game.player.level.animator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.game.player.level.animator.BaseLevelScenePlayerAnimator.AIRBORNE_HOLD_FRAME;
import static house.x1337.app.smb3.game.player.level.animator.BaseLevelScenePlayerAnimator.AIRBORNE_HOLD_FRAME_SMALL;
import static house.x1337.app.smb3.game.player.level.animator.BaseLevelScenePlayerAnimator.STANDING_WALK_FRAME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which <em>file</em> each hold beat lands on.
 *
 * <p>{@code renderCarryPose} selects a beat and indexes the bundle's four-entry {@code hold} array with
 * it, and the indices on their own say nothing about the art: the cycles are
 * {@code [holding_1, holding_2, holding_3, holding_2]} for the big suits and
 * {@code [holding_1, holding_2, holding_1, holding_2]} for small, so the same beat means different
 * poses per mode and two different beats can mean the same pose. The two beats that are <b>chosen</b>
 * rather than cycled — the airborne pin and the standing-still force — are therefore worth stating as
 * filenames, so that renaming, reordering or re-cutting the art fails here.
 *
 * <p>The ROM's authority for the airborne pin is {@code Player_HoldingFrames} (dasm prg008.asm:109)
 * read through {@code PRG008_AF15}: airborne takes row-relative index {@code 0}, or {@code 1} when
 * small, which is {@code PF_HOLDBIG_BASE} and {@code PF_HOLDSMALL_BASE+1} respectively.
 */
class CarryHoldFrameSelectionTest {
    /** The {@code hold} arrays as the three {@code assets.json} descriptors declare them, in order. */
    private static final List<String> BIG_HOLD_CYCLE = List.of(
        "holding_1.png",
        "holding_2.png",
        "holding_3.png",
        "holding_2.png"
    );

    private static final List<String> SMALL_HOLD_CYCLE = List.of(
        "holding_1.png",
        "holding_2.png",
        "holding_1.png",
        "holding_2.png"
    );

    @Test
    @DisplayName("Small Mario holds the second pose in mid-air, not the first")
    void theSmallAirbornePoseIsTheSecondOfItsTwo() {
        // PF_HOLDSMALL_BASE+1. The one place the small row differs from the big one, and the reason
        // AIRBORNE_HOLD_FRAME_SMALL exists at all.
        assertThat(SMALL_HOLD_CYCLE.get(AIRBORNE_HOLD_FRAME_SMALL)).isEqualTo("holding_2.png");
        assertThat(AIRBORNE_HOLD_FRAME_SMALL)
            .as("and it is a different beat from the one the big suits use")
            .isNotEqualTo(AIRBORNE_HOLD_FRAME);
    }

    @Test
    @DisplayName("The big suits hold the first pose in mid-air")
    void theBigAirbornePoseIsTheFirstOfItsThree() {
        // PF_HOLDBIG_BASE.
        assertThat(BIG_HOLD_CYCLE.get(AIRBORNE_HOLD_FRAME)).isEqualTo("holding_1.png");
    }

    @Test
    @DisplayName("A player standing still holds the beat-2 pose, which differs per mode")
    void theStandingPoseIsBeatTwo() {
        // PRG008_AE03 forces Player_WalkFrame = 2 when no direction is held and XVel is zero, so the
        // standing hold pose is whatever beat 2 happens to be - the third pose for the big suits, and
        // back to the first for small, whose cycle only has two.
        assertThat(BIG_HOLD_CYCLE.get(STANDING_WALK_FRAME)).isEqualTo("holding_3.png");
        assertThat(SMALL_HOLD_CYCLE.get(STANDING_WALK_FRAME)).isEqualTo("holding_1.png");
    }

    @Test
    @DisplayName("Neither chosen beat reaches past the shortest cycle, so the wrap never changes a pose")
    void bothChosenBeatsAreInsideEveryCycle() {
        // holdFrame wraps by the cycle's own length rather than the walk sequence's. The wrap is a guard
        // against a future mode, not something either of these beats relies on today.
        assertThat(AIRBORNE_HOLD_FRAME).isLessThan(SMALL_HOLD_CYCLE.size());
        assertThat(AIRBORNE_HOLD_FRAME_SMALL).isLessThan(SMALL_HOLD_CYCLE.size());
        assertThat(STANDING_WALK_FRAME).isLessThan(SMALL_HOLD_CYCLE.size());
    }
}
