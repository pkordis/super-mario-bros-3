package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.NORMAL_TRANSITION_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.RACCOON_TRANSITION_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        assertThat(runtimeState.getGrowShrinkCounter()).isEqualTo(NORMAL_TRANSITION_TICKS);
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
        assertThat(airborneState.getPlayerTailAttackCountdown())
            .as("A swing in progress survives: Player_GetHurt never writes Player_TailAttack, and the "
                + "strike it would disarm is resolved later in the same tick")
            .isEqualTo(7);
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
    // The transition must not disturb the carry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Finishing a transition discards the B press held across it, so no swing is armed")
    void finishingATransitionDiscardsTheHeldPress() {
        // The ROM arms a swing off Pad_Input - the buttons NEWLY pressed this frame (BIT Pad_Input / BVC,
        // dasm prg008 @ PRG008_B0D1). consumePress is a latch instead, and nothing reads B while the
        // player is tailless, so a B held since before a leaf was collected was still sitting unconsumed
        // when hasTail() turned true - and became an 18-tick swing that tail-struck the shell out of the
        // player's own hands nine ticks later.
        // Prepare - one tick left of the poof, with B down for the carry
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setQueuedMode(RACCOON);
        runtimeState.setPoofCounter(1);
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        final LevelScenePlayer player = transitioningPlayer(runtimeState, inputHandler);

        // Execute - the tick the suit lands
        player.tickModeTransition();

        // Verify
        verify(player).onTransitionComplete(RACCOON);
        verify(inputHandler).consumePress(HANDLER_RUN);
    }

    @Test
    @DisplayName("A transition still running consumes nothing, so a real press mid-grow is not eaten")
    void aRunningTransitionConsumesNothing() {
        // Prepare - well short of the end
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setQueuedMode(NORMAL);
        runtimeState.setGrowShrinkCounter(NORMAL_TRANSITION_TICKS);
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        final LevelScenePlayer player = transitioningPlayer(runtimeState, inputHandler);

        // Execute
        player.tickModeTransition();

        // Verify
        assertThat(runtimeState.getGrowShrinkCounter()).isEqualTo(NORMAL_TRANSITION_TICKS - 1);
        verify(player, never()).onTransitionComplete(any());
        verify(inputHandler, never()).consumePress(any());
    }

    @Test
    @DisplayName("Neither transition touches what the player is holding")
    void neitherTransitionTouchesTheCarry() {
        // The ROM's transitions write Player_QueueSuit, Player_Grow / Player_SuitLost, Player_FlashInv
        // and Player_Flip - never Player_IsHolding. A shell in your hands at the start of a grow is in
        // your hands at the end of it.
        // Prepare
        final CarriableLevelObject carried = mock(CarriableLevelObject.class);
        final PlayerRuntimeState growing = new PlayerRuntimeState();
        growing.setCarriedObject(carried);
        final PlayerRuntimeState poofing = new PlayerRuntimeState();
        poofing.setCarriedObject(carried);

        // Execute
        playerWithRealTransitions(growing, positionWithVelocity(0, 0)).turnToNormal();
        playerWithRealTransitions(poofing, positionWithVelocity(0, 0)).turnToRaccoon();

        // Verify
        assertThat(growing.getCarriedObject()).isSameAs(carried);
        assertThat(poofing.getCarriedObject()).isSameAs(carried);
        assertThat(growing.isCarrying()).isTrue();
        assertThat(poofing.isCarrying()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A player whose {@code tickModeTransition} runs for real, with the completion hook recorded. */
    private static LevelScenePlayer transitioningPlayer(
        final PlayerRuntimeState runtimeState,
        final PlayerInputHandler inputHandler
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(new PlayerPosition());
        when(player.getInputHandler()).thenReturn(inputHandler);
        doCallRealMethod().when(player).tickModeTransition();
        return player;
    }

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
