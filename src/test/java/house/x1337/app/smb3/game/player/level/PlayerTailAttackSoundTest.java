package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.SoundEffect.TAIL_WAG;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The tail swipe plays the tail wag once per swing it starts, and nothing for a press that starts none.
 */
class PlayerTailAttackSoundTest {
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();

    @Test
    @DisplayName("A B press that starts a swing plays the tail wag once, and a press mid-swing plays nothing")
    void aSwingPlaysTheTailWagOnce() {
        // Prepare
        final LevelScenePlayer player = player();
        when(inputHandler.consumePress(HANDLER_RUN)).thenReturn(true);

        // Execute
        player.handleTailAttack(inputHandler);
        player.handleTailAttack(inputHandler);

        // Verify
        verify(soundPlayer, times(1)).play(TAIL_WAG);
    }

    @Test
    @DisplayName("A B press while ducking starts no swing and plays nothing")
    void aPressWhileDuckingIsSilent() {
        // Prepare
        final LevelScenePlayer player = player();
        when(inputHandler.isActive(HANDLER_DOWN)).thenReturn(true);
        when(inputHandler.consumePress(HANDLER_RUN)).thenReturn(true);

        // Execute
        player.handleTailAttack(inputHandler);

        // Verify
        verifyNoInteractions(soundPlayer);
    }

    private LevelScenePlayer player() {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getSoundPlayer()).thenReturn(soundPlayer);
        doCallRealMethod().when(player).handleTailAttack(inputHandler);
        return player;
    }
}
