package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.KICK_POSE_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The player's half of the kick: the 12-tick pose, the tail swing it cancels, the facing a contact kick
 * rewrites, and the one entry point that deliberately shows no pose at all.
 *
 * <p>{@code Player_Kick = $0C} is set in {@code Player_KickObject} (dasm prg000.asm:2835-2836) and spent
 * by the frame-selection block at {@code PRG008_AF52}, which opens by zeroing {@code Player_TailAttack}
 * and closes by decrementing the counter. Both of those live together here for the same reason a stomp
 * gets neither: it zeroes {@code Player_Kick} the instant the kick returns, so that whole block — pose
 * <em>and</em> tail cancel — never runs for it.
 *
 * <p>Counter arithmetic rather than rendering throughout, because the animator needs jME to assert
 * against. The pose is mode-independent by construction: it lives in
 * {@code BaseLevelScenePlayerAnimator} and every bundle ships exactly one kick frame, whose geometry is
 * {@code KickPoseAnchoringTest}'s subject.
 */
class PlayerKickPoseTest {
    @Test
    @DisplayName("The kick frame is held for exactly twelve ticks")
    void theKickFrameIsHeldForTwelveTicks() {
        // Set to $0C and decremented once per drawn frame, so the counter values 12 down to 1 are all
        // drawn as the kick - the same set-then-decrement shape as the camera-facing carry pose.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = kickingPlayer(runtimeState, RIGHT);
        player.kickCarriedObject(mock(CarriableLevelObject.class));

        // Execute & Verify
        int posedTicks = 0;
        for (int tick = 0; tick < 20; tick++) {
            if (runtimeState.isKicking()) {
                posedTicks++;
            }
            player.tickKickPose();
        }

        assertThat(posedTicks).isEqualTo(KICK_POSE_TICKS);
        assertThat(runtimeState.isKicking()).isFalse();
    }

    @Test
    @DisplayName("Arming the kick pose cancels a tail swing in progress, on the same tick")
    void theKickCancelsTheTailSwing() {
        // dasm prg008.asm:3239-3240: the kick-frame block writes Player_TailAttack = 0 before it writes
        // the frame. It has to land immediately rather than next tick, because the object pass resolves
        // body contact and then the tail - so a contact kick decided in the first half would otherwise
        // still be swinging in the second, and a raccoon would kick and strike at once.
        // Prepare - mid-swing, on a frame that would strike
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setPlayerTailAttackCountdown(12);
        final LevelScenePlayer player = kickingPlayer(runtimeState, RIGHT);

        // Execute
        player.kickCarriedObject(mock(CarriableLevelObject.class));

        // Verify
        assertThat(runtimeState.getPlayerTailAttackCountdown()).isZero();
        assertThat(runtimeState.isKicking()).isTrue();
    }

    @Test
    @DisplayName("A release kick sends the object in the player's facing and does not turn them")
    void aReleaseKickKeepsTheFacing() {
        // PRG000_CE79's held branch takes the direction from Player_FlipBits and writes nothing back to
        // it: only a contact kick turns the player.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = kickingPlayer(runtimeState, LEFT);
        final CarriableLevelObject carried = mock(CarriableLevelObject.class);

        // Execute
        player.kickCarriedObject(carried);

        // Verify
        verify(carried).onKicked(player, false);
        assertThat(player.getOrientation().getHorizontal()).as("unchanged by its own kick").isEqualTo(LEFT);
    }

    @Test
    @DisplayName("A contact kick turns the player towards the object, whichever side it is on")
    void aContactKickTurnsThePlayerTowardsTheObject() {
        // PlayerKickFlipBits = {$00, $40} indexed by Level_ObjCalcXDiffs (dasm prg000.asm:2797, :2953).
        // A rare case of an object rewriting the player's orientation, so it goes through the player's
        // own orientation state rather than any field the renderer reads directly.
        // Prepare - a player facing left with the object to their right
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = kickingPlayer(runtimeState, LEFT);
        final CarriableLevelObject onTheRight = carriableAt(100);

        // Execute
        player.kickOnContact(onTheRight);

        // Verify
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(RIGHT);
        verify(onTheRight).onKicked(player, true);

        // Execute - and the mirror of it
        final CarriableLevelObject onTheLeft = carriableAt(-100);
        player.kickOnContact(onTheLeft);

        // Verify
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(LEFT);
        verify(onTheLeft).onKicked(player, false);
    }

    @Test
    @DisplayName("A stomp kick shows no pose and leaves a tail swing running")
    void aStompKickShowsNoPose() {
        // PRG000_D2A2 zeroes Player_Kick the instant Player_KickObject returns, so PRG008_AF52 never
        // runs for a stomp - which costs it the kick frame AND the tail cancel that shares that block.
        // Expressed as not arming the pose, rather than as arming it and undoing it.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setPlayerTailAttackCountdown(12);
        final LevelScenePlayer player = kickingPlayer(runtimeState, LEFT);
        final CarriableLevelObject stomped = carriableAt(100);

        // Execute
        player.kickWithoutPose(stomped);

        // Verify
        assertThat(runtimeState.isKicking()).as("no kick frame for a stomp").isFalse();
        assertThat(runtimeState.getPlayerTailAttackCountdown())
            .as("and so no tail cancel either, which rides the same block")
            .isEqualTo(12);
        verify(stomped).onKicked(eq(player), anyBoolean());
        assertThat(player.getOrientation().getHorizontal())
            .as("the facing rewrite is not part of that block, so it still happens")
            .isEqualTo(RIGHT);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A player whose kick entry points run for real against the given state, and nothing else does. */
    private static LevelScenePlayer kickingPlayer(
        final PlayerRuntimeState runtimeState,
        final PlayerOrientationHorizontal facing
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(new PlayerPosition());
        when(player.getOrientation()).thenReturn(new PlayerOrientation(facing, SUSTAINED));
        when(player.asLevelScenePlayer()).thenReturn(player);
        doCallRealMethod().when(player).kickCarriedObject(any());
        doCallRealMethod().when(player).kickOnContact(any());
        doCallRealMethod().when(player).kickWithoutPose(any());
        doCallRealMethod().when(player).tickKickPose();
        return player;
    }

    private static CarriableLevelObject carriableAt(final double pixelX) {
        final CarriableLevelObject carriable = mock(CarriableLevelObject.class);
        when(carriable.getPosition()).thenReturn(new PixelPosition(pixelX, 0));
        return carriable;
    }
}
