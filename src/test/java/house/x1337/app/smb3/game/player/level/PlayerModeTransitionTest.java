package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.NORMAL_TRANSITION_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.RACCOON_TRANSITION_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a mode transition may and may not neutralise.
 *
 * <p>The transition freezes the player for its whole duration, so whatever state it leaves behind is the
 * state the first resumed frame is resolved against. A grounded player is neutralised to a standing pose;
 * an airborne one must keep the jump it was in the middle of, because the movement mode is what tells
 * collision the player is airborne and {@code DY}'s sign is what exempts a one-way platform from being
 * solid.
 */
class PlayerModeTransitionTest {
    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("Growing mid-jump keeps the player airborne and its rising velocity intact")
    void growingMidAirPreservesVerticalMotion() {
        // Prepare - rising through the air with horizontal momentum
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING);
        final PlayerPosition position = positionWithVelocity(2.5, -3.0);
        final LevelScenePlayer player = playerWithRealTransitions(runtimeState, position);

        // Execute
        player.turnToNormal();

        // Verify
        assertThat(runtimeState.getGrowCounter()).isEqualTo(NORMAL_TRANSITION_TICKS);
        assertThat(runtimeState.getMovement())
            .as("A player frozen mid-jump must not be reported as standing")
            .isEqualTo(JUMPING);
        assertThat(runtimeState.isInAir()).isTrue();
        assertThat(position.getDY())
            .as("The rising velocity is what exempts a one-way platform from being solid")
            .isCloseTo(-3.0, within(TOLERANCE));
        assertThat(position.getDX())
            .as("Horizontal momentum survives: no transition in the ROM writes Player_XVel")
            .isCloseTo(2.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("Growing while falling keeps the fall going")
    void growingWhileFallingPreservesDescent() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING);
        final PlayerPosition position = positionWithVelocity(0, 2.0);
        final LevelScenePlayer player = playerWithRealTransitions(runtimeState, position);

        // Execute
        player.turnToNormal();

        // Verify
        assertThat(runtimeState.isInAir()).isTrue();
        assertThat(position.getDY()).isCloseTo(2.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Growing on the ground settles vertically but keeps the run going")
    void growingOnTheGroundNeutralisesMotion() {
        // Prepare - running along the ground
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        runtimeState.duck();
        final PlayerPosition position = positionWithVelocity(3.0, 0);
        final LevelScenePlayer player = playerWithRealTransitions(runtimeState, position);

        // Execute
        player.turnToNormal();

        // Verify
        assertThat(runtimeState.getMovement()).isEqualTo(STILL);
        assertThat(runtimeState.isDucking()).as("The grow pose always stands up").isFalse();
        assertThat(position.getDX())
            .as("Player_XVel is never written by a transition, so the speed carries through")
            .isCloseTo(3.0, within(TOLERANCE));
        assertThat(position.getDY()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("The leaf poof follows the same rule: airborne keeps its motion, grounded is neutralised")
    void poofFollowsTheSameRule() {
        // Prepare - airborne, mid-flight
        final PlayerRuntimeState airborneState = new PlayerRuntimeState();
        airborneState.setTo(FLYING);
        airborneState.setPlayerFlyTime(40);
        airborneState.setPlayerWagCount(5);
        airborneState.setPlayerTailAttackCountdown(7);
        final PlayerPosition airbornePosition = positionWithVelocity(2.0, -1.5);

        // Execute
        playerWithRealTransitions(airborneState, airbornePosition).turnToRaccoon();

        // Verify - the flight abilities are spent, but the player is still in the air and still rising
        assertThat(airborneState.getPoofCounter()).isEqualTo(RACCOON_TRANSITION_TICKS);
        assertThat(airborneState.isInAir()).isTrue();
        assertThat(airborneState.getPlayerFlyTime()).isZero();
        assertThat(airborneState.getPlayerWagCount()).isZero();
        assertThat(airborneState.getPlayerTailAttackCountdown()).isZero();
        assertThat(airbornePosition.getDY()).isCloseTo(-1.5, within(TOLERANCE));

        // Prepare & Execute - the grounded counterpart
        final PlayerRuntimeState groundedState = new PlayerRuntimeState();
        groundedState.setTo(RUNNING);
        final PlayerPosition groundedPosition = positionWithVelocity(3.0, 0);
        playerWithRealTransitions(groundedState, groundedPosition).turnToRaccoon();

        // Verify
        assertThat(groundedState.getMovement()).isEqualTo(STILL);
        assertThat(groundedPosition.getDX())
            .as("the poof costs the player their suit, not their speed")
            .isCloseTo(3.0, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * A player whose transition entry points run for real against the given state, so the neutralisation
     * itself is under test rather than a stub of it.
     */
    private static LevelScenePlayer playerWithRealTransitions(
        final PlayerRuntimeState runtimeState,
        final PlayerPosition position
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(position);
        doCallRealMethod().when(player).turnToNormal();
        doCallRealMethod().when(player).turnToRaccoon();
        return player;
    }

    private static PlayerPosition positionWithVelocity(final double dx, final double dy) {
        final PlayerPosition position = new PlayerPosition();
        position.setDX(dx);
        position.setDY(dy);
        return position;
    }
}
