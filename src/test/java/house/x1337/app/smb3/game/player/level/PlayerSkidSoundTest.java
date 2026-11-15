package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VELOCITY_THRESHOLD;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.enumeration.SoundEffect.SKID;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The skid sound is asked for on every tick the player skids: on the ground, moving at least at the skid speed and
 * holding the opposite direction only. That it then repeats back to back rather than restarting is the sound's
 * channel rule, covered by {@code SoundPlayerTest}.
 */
class PlayerSkidSoundTest {
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
    private final PlayerPosition position = new PlayerPosition();

    @Test
    @DisplayName("Holding left while moving right on the ground skids and asks for the skid sound")
    void aSkidAsksForTheSound() {
        // Prepare
        final LevelScenePlayer player = player(PLAYER_SKID_VELOCITY_THRESHOLD, true, false);

        // Execute
        player.refinePlayerState(inputHandler, false, false);

        // Verify
        assertThat(runtimeState.getMovement()).isEqualTo(SKIDDING);
        verify(soundPlayer, times(1)).play(SKID);
    }

    @Test
    @DisplayName("Every tick of a skid asks for the sound again, either way round")
    void everySkiddingTickAsksAgain() {
        // Prepare
        final LevelScenePlayer player = player(-1.5, false, true);

        // Execute
        for (int tick = 0; tick < 12; tick++) {
            player.refinePlayerState(inputHandler, false, false);
        }

        // Verify
        verify(soundPlayer, times(12)).play(SKID);
    }

    @Test
    @DisplayName("Below the skid speed there is no skid and no sound")
    void tooSlowToSkid() {
        // Prepare
        final LevelScenePlayer player = player(PLAYER_SKID_VELOCITY_THRESHOLD / 2, true, false);

        // Execute
        player.refinePlayerState(inputHandler, false, false);

        // Verify
        assertThat(runtimeState.getMovement()).isEqualTo(WALKING);
        verify(soundPlayer, never()).play(SKID);
    }

    @Test
    @DisplayName("Holding the direction of travel is no skid")
    void holdingTheDirectionOfTravelIsNoSkid() {
        // Prepare
        final LevelScenePlayer player = player(1.5, false, true);

        // Execute
        player.refinePlayerState(inputHandler, false, false);

        // Verify
        verify(soundPlayer, never()).play(SKID);
    }

    @Test
    @DisplayName("Holding both directions is no skid")
    void holdingBothDirectionsIsNoSkid() {
        // Prepare
        final LevelScenePlayer player = player(1.5, true, true);

        // Execute
        player.refinePlayerState(inputHandler, false, false);

        // Verify
        verify(soundPlayer, never()).play(SKID);
    }

    @Test
    @DisplayName("In the air there is no skid and no sound")
    void noSkidInTheAir() {
        // Prepare
        final LevelScenePlayer player = player(1.5, true, false);
        runtimeState.setTo(FALLING);

        // Execute
        player.refinePlayerState(inputHandler, false, false);

        // Verify
        assertThat(runtimeState.getMovement()).isEqualTo(FALLING);
        verify(soundPlayer, never()).play(SKID);
    }

    /** A grounded player moving at {@code dx}, holding left and right as given, whose state refining is real. */
    private LevelScenePlayer player(final double dx, final boolean left, final boolean right) {
        position.setDX(dx);
        when(inputHandler.isActive(HANDLER_LEFT)).thenReturn(left);
        when(inputHandler.isActive(HANDLER_RIGHT)).thenReturn(right);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(position);
        when(player.getSoundPlayer()).thenReturn(soundPlayer);
        doCallRealMethod().when(player).refinePlayerState(inputHandler, false, false);
        doCallRealMethod().when(player).isCurrentlySkidding(anyBoolean(), anyBoolean());
        return player;
    }
}
