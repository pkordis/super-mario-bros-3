package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.game.player.level.animator.BaseLevelScenePlayerAnimator.FACE_FORWARD_FRAME;
import static house.x1337.app.smb3.game.player.level.animator.BaseLevelScenePlayerAnimator.pipePoseFrame;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.PIPE_ENTRY_TICKS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pipe pose: face forward going up or down, the suit's walk cycle going left or right, indexed by
 * {@code (frame counter & 12) >> 2}.
 */
class PipePoseFrameSelectionTest {
    // Walk cycles: small walks 0, 1, 0, 1 and every big suit 0, 1, 2, 1
    private static final int[] SMALL_WALK = {0, 1, 0, 1};
    private static final int[] BIG_WALK = {0, 1, 2, 1};

    @ParameterizedTest
    @EnumSource(value = PipeEntryDirection.class, names = {"DOWN", "UP"})
    @DisplayName("Going up or down, every tick of the entry is drawn facing forward")
    void verticalPipesFaceForward(final PipeEntryDirection direction) {
        // Prepare
        final PlayerRuntimeState runtimeState = entering(direction);

        // Execute & Verify
        for (int tick = 0; tick < PIPE_ENTRY_TICKS; tick++) {
            assertThat(pipePoseFrame(runtimeState, BIG_WALK)).as("Tick %d", tick).isEqualTo(FACE_FORWARD_FRAME);
            assertThat(pipePoseFrame(runtimeState, SMALL_WALK)).as("Tick %d", tick).isEqualTo(FACE_FORWARD_FRAME);
            runtimeState.decrementPipeCountdown();
        }
    }

    @Test
    @DisplayName("Going sideways, the walk cycle moves on one frame every four ticks")
    void sidePipesWalkOneFrameEveryFourTicks() {
        // Prepare
        final PlayerRuntimeState runtimeState = entering(RIGHT);
        final StringBuilder big = new StringBuilder();
        final StringBuilder small = new StringBuilder();

        // Execute - ticks 1 to 16 of the walk
        for (int tick = 1; tick <= 16; tick++) {
            runtimeState.decrementPipeCountdown();
            big.append(pipePoseFrame(runtimeState, BIG_WALK));
            small.append(pipePoseFrame(runtimeState, SMALL_WALK));
        }

        // Verify
        assertThat(big).hasToString("0001111222211110");
        assertThat(small).hasToString("0001111000011110");
    }

    private static PlayerRuntimeState entering(final PipeEntryDirection direction) {
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.startEnteringPipe(PipePortal.builder().build(), direction);
        return runtimeState;
    }
}
