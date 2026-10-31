package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_PAUSE_TICKS;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_RISE_YVEL;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.THROE;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pit death itself (dasm prg008 @ PRG008_A3FA): whatever the suit, {@code Player_Die} followed by
 * {@code Player_IsDying = 2}, which skips the throe. The check that triggers it is
 * {@code PlayerPitFallTest}'s subject; the ending is {@code PlayerDeathEventChainTest}'s.
 */
class PlayerPitDeathTest {
    private static final double TOLERANCE = 1e-9;

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Falling into a pit kills the player in every suit, with no pause and no rise")
    void aPitKillsEverySuitWithNoThroe(final PlayerMode mode) {
        // Prepare - running right while falling
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final PlayerPosition position = positionWithVelocity(2.5, 4.0);
        final LevelScenePlayer player = playerIn(mode, runtimeState, position);

        // Execute
        player.dieByFallingOff();

        // Verify
        assertThat(runtimeState.isDying()).as("Dying").isTrue();
        assertThat(runtimeState.getDeathKind()).as("Player_IsDying = 2").isEqualTo(FELL_OFF);
        assertThat(runtimeState.isDeathPausing()).as("No 48-tick hang").isFalse();
        assertThat(position.getDX()).as("Player_Die zeroes XVel").isCloseTo(0.0, within(TOLERANCE));
        assertThat(position.getDY()).as("No rise velocity").isCloseTo(0.0, within(TOLERANCE));
        assertThat(runtimeState.isSpriteDrawnThisTick()).as("Player_Die_FellOff draws nothing").isFalse();
        verify(player).announceDeath(FELL_OFF);
    }

    @Test
    @DisplayName("A pit kills a flashing player, whom a hit could not touch, and ends the flashing")
    void aPitKillsAFlashingPlayer() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setHurtInvincibilityCounter(40);
        final LevelScenePlayer player = playerIn(SHRUNK, runtimeState, positionWithVelocity(0, 3.0));
        final boolean hurtableBefore = player.isHurtable();

        // Execute
        player.dieByFallingOff();

        // Verify
        assertThat(hurtableBefore).as("The flashing shields it from a hit").isFalse();
        assertThat(runtimeState.getDeathKind()).as("But not from a pit").isEqualTo(FELL_OFF);
        assertThat(runtimeState.getHurtInvincibilityCounter()).as("Player_Die clears Player_FlashInv").isZero();
    }

    @Test
    @DisplayName("A pit kills a player who is carrying, flying and tail-wagging, and spends those abilities")
    void aPitKillsACarryingFlyingPlayer() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setCarriedObject(mock(CarriableLevelObject.class));
        runtimeState.setTo(FLYING);
        runtimeState.setPlayerFlyTime(0x40);
        runtimeState.setPlayerWagCount(8);
        runtimeState.setPlayerTailAttackCountdown(6);
        final LevelScenePlayer player = playerIn(RACCOON, runtimeState, positionWithVelocity(1.0, 1.0));

        // Execute
        player.dieByFallingOff();

        // Verify
        assertThat(runtimeState.getDeathKind()).isEqualTo(FELL_OFF);
        assertThat(runtimeState.getPlayerFlyTime()).as("Fly time").isZero();
        assertThat(runtimeState.getPlayerWagCount()).as("Wag count").isZero();
        assertThat(runtimeState.getPlayerTailAttackCountdown()).as("Tail swing").isZero();
    }

    @Test
    @DisplayName("A player already in the death throe is left alone, so the death is announced once")
    void aDyingPlayerIsLeftAlone() {
        // Prepare - killed by a hit, still rising
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final PlayerPosition position = positionWithVelocity(0, 0);
        final LevelScenePlayer player = playerIn(SHRUNK, runtimeState, position);
        player.die();

        // Execute
        player.dieByFallingOff();

        // Verify
        assertThat(runtimeState.getDeathKind()).as("Still the throe").isEqualTo(THROE);
        assertThat(runtimeState.getDeathPauseCounter()).isEqualTo(PLAYER_DEATH_PAUSE_TICKS);
        assertThat(position.getDY()).isCloseTo(PLAYER_DEATH_RISE_YVEL, within(TOLERANCE));
        verify(player, times(1)).announceDeath(THROE);
        verify(player, never()).announceDeath(FELL_OFF);
    }

    @Test
    @DisplayName("A second pit check on a player who already fell is ignored")
    void aSecondPitCheckIsIgnored() {
        // Prepare
        final LevelScenePlayer player = playerIn(SHRUNK, new PlayerRuntimeState(), positionWithVelocity(0, 0));
        player.dieByFallingOff();

        // Execute
        player.dieByFallingOff();

        // Verify
        verify(player, times(1)).announceDeath(any());
    }

    @Test
    @DisplayName("The hurt death is unchanged: the throe opens with the pause and the rise, and is drawn")
    void theHurtDeathIsUnchanged() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final PlayerPosition position = positionWithVelocity(1.5, 0);
        final LevelScenePlayer player = playerIn(SHRUNK, runtimeState, position);

        // Execute
        player.getHurtBy(null);

        // Verify
        assertThat(runtimeState.getDeathKind()).isEqualTo(THROE);
        assertThat(runtimeState.isDeathPausing()).isTrue();
        assertThat(position.getDY()).isCloseTo(PLAYER_DEATH_RISE_YVEL, within(TOLERANCE));
        assertThat(runtimeState.isSpriteDrawnThisTick()).as("The dead frame is drawn").isTrue();
        verify(player).announceDeath(THROE);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A player whose death entry points run for real against the given state. */
    private static LevelScenePlayer playerIn(
        final PlayerMode mode,
        final PlayerRuntimeState runtimeState,
        final PlayerPosition position
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getMode()).thenReturn(mode);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(position);
        doCallRealMethod().when(player).isLarge();
        doCallRealMethod().when(player).isAdvanced();
        doCallRealMethod().when(player).isHurtable();
        doCallRealMethod().when(player).getHurtBy(null);
        doCallRealMethod().when(player).die();
        doCallRealMethod().when(player).dieByFallingOff();
        return player;
    }

    private static PlayerPosition positionWithVelocity(final double dx, final double dy) {
        final PlayerPosition position = new PlayerPosition();
        position.setDX(dx);
        position.setDY(dy);
        return position;
    }
}
