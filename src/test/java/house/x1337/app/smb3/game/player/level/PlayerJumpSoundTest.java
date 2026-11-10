package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.SoundEffect.JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The jump sound plays when a jump starts from the ground, once per press, and not for a press in the air.
 */
class PlayerJumpSoundTest {
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();

    @Test
    @DisplayName("A jump from the ground plays the jump sound once")
    void aGroundJumpPlaysTheJumpSound() {
        // Prepare
        final LevelScenePlayer player = player(false);
        when(inputHandler.consumePress(HANDLER_JUMP)).thenReturn(true);

        // Execute
        player.handleVerticalMovement(inputHandler);

        // Verify
        assertThat(runtimeState.getMovement()).as("The jump started").isEqualTo(JUMPING);
        verify(soundPlayer, times(1)).play(JUMP);
    }

    @Test
    @DisplayName("Holding the jump button through the jump plays the sound only for the press")
    void holdingTheButtonPlaysTheSoundOnce() {
        // Prepare
        final LevelScenePlayer player = player(false);
        when(inputHandler.consumePress(HANDLER_JUMP)).thenReturn(true, false);
        when(inputHandler.isActive(HANDLER_JUMP)).thenReturn(true);

        // Execute
        for (int tick = 0; tick < 10; tick++) {
            player.handleVerticalMovement(inputHandler);
        }

        // Verify
        verify(soundPlayer, times(1)).play(JUMP);
    }

    @Test
    @DisplayName("A press in the air, a tail wag, plays no jump sound")
    void aPressInTheAirPlaysNoJumpSound() {
        // Prepare
        final LevelScenePlayer player = player(true);
        runtimeState.setTo(FALLING);
        when(inputHandler.consumePress(HANDLER_JUMP)).thenReturn(true);

        // Execute
        player.handleVerticalMovement(inputHandler);

        // Verify
        assertThat(runtimeState.getPlayerWagCount()).as("The press wagged the tail").isPositive();
        verifyNoInteractions(soundPlayer);
    }

    /** A player whose vertical movement runs for real against real state, with a tail or without. */
    private LevelScenePlayer player(final boolean tail) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(new PlayerPosition());
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, SUSTAINED));
        when(player.getPlayerData()).thenReturn(mock(PlayerData.class));
        when(player.getSoundPlayer()).thenReturn(soundPlayer);
        when(player.hasTail()).thenReturn(tail);
        doCallRealMethod().when(player).handleVerticalMovement(inputHandler);
        return player;
    }
}
