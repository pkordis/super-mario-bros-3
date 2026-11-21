package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.GRAVITY_FAST;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A jump pressed while riding a note block boosts its launch instead of jumping or wagging the tail, and holding the
 * jump does not stretch the launch.
 */
class NoteBlockRideMovementTest {
    private static final double TOLERANCE = 1e-9;

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
    private final PlayerPosition position = new PlayerPosition();

    @Test
    @DisplayName("A jump pressed during the ride boosts the launch and neither jumps nor wags the tail")
    void aPressDuringTheRideBoostsTheLaunch() {
        // Prepare
        final LevelScenePlayer player = player();
        runtimeState.setTo(FALLING);
        runtimeState.setRidingNoteBlock(true);
        position.setDY(3.0);
        when(inputHandler.consumePress(HANDLER_JUMP)).thenReturn(true);

        // Execute
        player.handleVerticalMovement(inputHandler);

        // Verify
        assertThat(runtimeState.isNoteBlockBoosted()).isTrue();
        assertThat(runtimeState.getPlayerWagCount()).as("No tail wag").isZero();
        assertThat(position.getDY()).as("Only gravity acts")
            .isCloseTo(3.0 + GRAVITY_FAST / TILE_SPRITE_SIZE, within(TOLERANCE));
        verifyNoInteractions(soundPlayer);
    }

    @Test
    @DisplayName("A jump held without a new press during the ride does not boost the launch")
    void holdingTheJumpDoesNotBoost() {
        // Prepare
        final LevelScenePlayer player = player();
        runtimeState.setTo(FALLING);
        runtimeState.setRidingNoteBlock(true);
        when(inputHandler.isActive(HANDLER_JUMP)).thenReturn(true);

        // Execute
        player.handleVerticalMovement(inputHandler);

        // Verify
        assertThat(runtimeState.isNoteBlockBoosted()).isFalse();
    }

    @Test
    @DisplayName("After a launch, holding the jump does not slow the rise until it has slowed on its own")
    void holdingTheJumpDoesNotStretchTheLaunch() {
        // Prepare
        final LevelScenePlayer player = player();
        runtimeState.setTo(JUMPING);
        runtimeState.setSlowGravityBlocked(true);
        position.setDY(-3.5);
        when(inputHandler.isActive(HANDLER_JUMP)).thenReturn(true);

        // Execute & Verify
        player.handleVerticalMovement(inputHandler);
        assertThat(position.getDY()).as("Heavy gravity while rising fast")
            .isCloseTo(-3.5 + GRAVITY_FAST / TILE_SPRITE_SIZE, within(TOLERANCE));

        position.setDY(-1.5);
        player.handleVerticalMovement(inputHandler);
        assertThat(runtimeState.isSlowGravityBlocked()).as("Lifted once the rise is slow").isFalse();

        position.setDY(-3.5);
        player.handleVerticalMovement(inputHandler);
        assertThat(position.getDY()).as("A later jump held rises slowly again")
            .isCloseTo(-3.5 + GRAVITY_SLOW / TILE_SPRITE_SIZE, within(TOLERANCE));
    }

    private LevelScenePlayer player() {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(position);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, SUSTAINED));
        when(player.getPlayerData()).thenReturn(mock(PlayerData.class));
        when(player.getSoundPlayer()).thenReturn(soundPlayer);
        when(player.hasTail()).thenReturn(true);
        doCallRealMethod().when(player).handleVerticalMovement(inputHandler);
        return player;
    }
}
