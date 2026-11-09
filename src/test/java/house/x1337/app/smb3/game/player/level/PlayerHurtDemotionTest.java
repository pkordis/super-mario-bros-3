package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMode.TANOOKI;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.HURT_INVINCIBILITY_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.NORMAL_TRANSITION_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.SUIT_LOSS_TRANSITION_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the advanced→Normal suit loss a damaging hit causes.
 *
 * <p>Three things have to happen together, at the same moment: the shared suit-loss poof starts, the queued
 * suit names Normal as where it lands, and the flashing invincibility is armed. Arming it here rather than
 * when the poof ends is safe precisely because nothing decrements it while the poof is on screen — see
 * {@link HurtInvincibilityFlickerTest}.
 */
class PlayerHurtDemotionTest {
    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"RACCOON", "TANOOKI"})
    @DisplayName("An advanced suit is poofed off and queued back to Normal, leaving the player invincible")
    void advancedSuitIsLostToNormal(final PlayerMode advancedMode) {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        final LevelScenePlayer player = hurtablePlayer(advancedMode, runtimeState, positionWithVelocity(3.0, 0));

        // Execute
        player.getHurtBy(null);

        // Verify
        assertThat(runtimeState.getPoofCounter())
            .as("The same 23-tick poof the Super Leaf plays")
            .isEqualTo(SUIT_LOSS_TRANSITION_TICKS);
        assertThat(runtimeState.getQueuedMode())
            .as("Queued suit is back to plain Super")
            .isEqualTo(NORMAL);
        assertThat(runtimeState.getHurtInvincibilityCounter()).isEqualTo(HURT_INVINCIBILITY_TICKS);
        assertThat(runtimeState.isTransitioning())
            .as("the poof halts gameplay for its whole duration")
            .isTrue();
        assertThat(runtimeState.getMovement())
            .as("a grounded player is neutralised to a standing pose, as every transition does")
            .isEqualTo(STILL);
    }

    @Test
    @DisplayName("A big Normal player shrinks, riding the grow counter in reverse and flashing during it")
    void bigNormalPlayerShrinks() {
        // A hit shrinks Super here: the suit is queued as small, the grow counter is set to 47 (the same
        // counter the mushroom grow uses, played backwards), and the flashing invincibility to 113. Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        final LevelScenePlayer player = hurtablePlayer(NORMAL, runtimeState, positionWithVelocity(3.0, 0));

        // Execute
        player.getHurtBy(null);

        // Verify
        assertThat(runtimeState.getGrowShrinkCounter())
            .as("The same 47-tick counter the small→Super grow runs")
            .isEqualTo(NORMAL_TRANSITION_TICKS);
        assertThat(runtimeState.getPoofCounter()).as("a shrink is not a poof").isZero();
        assertThat(runtimeState.getQueuedMode())
            .as("Queued suit is down to small")
            .isEqualTo(SHRUNK);
        assertThat(runtimeState.getHurtInvincibilityCounter()).isEqualTo(HURT_INVINCIBILITY_TICKS);
        assertThat(runtimeState.isTransitioning())
            .as("the grow counter halts gameplay for the whole shrink")
            .isTrue();
        assertThat(runtimeState.getMovement())
            .as("a grounded player is neutralised to a standing pose, as every transition does")
            .isEqualTo(STILL);
    }

    @Test
    @DisplayName("The shrink flickers the whole way: the flash advances every grow tick, unlike a poof")
    void invincibilityRunsThroughTheShrink() {
        // A grow/shrink still draws the player every tick, and drawing the player is where the flash counts
        // down — so unlike the poof, the flash runs during the transition and the player visibly flickers
        // while shrinking. Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        final LevelScenePlayer player = hurtablePlayer(NORMAL, runtimeState, positionWithVelocity(0, 0));
        doCallRealMethod().when(player).tickModeTransition();
        player.getHurtBy(null);

        // Execute - run the shrink out, watching the flash count down in lockstep with the grow
        int expectedFlash = HURT_INVINCIBILITY_TICKS;
        for (int tick = 0; tick < NORMAL_TRANSITION_TICKS; tick++) {
            assertThat(runtimeState.isChangingSize()).as("still shrinking at tick %d", tick).isTrue();
            assertThat(runtimeState.getHurtInvincibilityCounter())
                .as("flash advances with the shrink at tick %d", tick)
                .isEqualTo(expectedFlash);
            player.tickModeTransition();
            expectedFlash--;
        }

        // Verify - the shrink is over and the flash kept ticking right through it
        assertThat(runtimeState.isTransitioning()).isFalse();
        assertThat(runtimeState.getHurtInvincibilityCounter())
            .as("the flash spent NORMAL_TRANSITION_TICKS of its life inside the shrink")
            .isEqualTo(HURT_INVINCIBILITY_TICKS - NORMAL_TRANSITION_TICKS);
        verify(player).onTransitionComplete(SHRUNK);
    }

    @Test
    @DisplayName("A small player dies: the hit starts the death throe, not a shrink or a poof")
    void smallPlayerDies() {
        // A hit kills a small player: the horizontal velocity is zeroed, the player rises at -4 px/frame and
        // starts dying — no suit change, no poof, no grow counter.
        // Prepare - running, to prove the throe zeroes horizontal motion
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        final PlayerPosition position = positionWithVelocity(3.0, 0);
        final LevelScenePlayer player = hurtablePlayer(SHRUNK, runtimeState, position);

        // Execute
        player.getHurtBy(null);

        // Verify
        assertThat(runtimeState.isDying()).as("the death throe has begun").isTrue();
        assertThat(runtimeState.isDeathPausing())
            .as("the throe opens with the still pause, body held in place before launching")
            .isTrue();
        assertThat(runtimeState.getDeathPauseCounter())
            .as("Death pause seeded to 48 ticks")
            .isEqualTo(48);
        assertThat(runtimeState.getPoofCounter()).as("a death is not a poof").isZero();
        assertThat(runtimeState.getGrowShrinkCounter()).as("nor a shrink").isZero();
        assertThat(runtimeState.getHurtInvincibilityCounter()).as("death grants no flashing").isZero();
        assertThat(position.getDX()).as("Horizontal velocity is zeroed").isZero();
        assertThat(position.getDY()).as("Rises at -4.0 px/frame").isEqualTo(-4.0);
    }

    @Test
    @DisplayName("A dying player cannot be hurt again, so a second contact does not restart the throe")
    void aDyingPlayerAbsorbsFurtherHits() {
        // Prepare - already dying
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = hurtablePlayer(SHRUNK, runtimeState, positionWithVelocity(0, 0));
        player.getHurtBy(null);
        assertThat(runtimeState.isDying()).isTrue();

        // Execute - a second hit mid-throe
        player.getHurtBy(null);

        // Verify - still a no-op; the throe is one-way
        assertThat(player.isHurtable()).isFalse();
        assertThat(runtimeState.isDying()).isTrue();
    }

    @Test
    @DisplayName("A hit lands only once: the invincibility it leaves behind absorbs the next one")
    void invincibilityAbsorbsTheNextHit() {
        // Prepare - already flashing from an earlier hit, and back in one piece
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setHurtInvincibilityCounter(40);
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, positionWithVelocity(0, 0));

        // Execute
        player.getHurtBy(null);

        // Verify
        assertThat(player.isHurtable()).isFalse();
        assertThat(runtimeState.getPoofCounter()).as("no second poof").isZero();
        assertThat(runtimeState.getHurtInvincibilityCounter())
            .as("nor is the running timer refreshed")
            .isEqualTo(40);
    }

    @Test
    @DisplayName("A hit during a transition is ignored, so the poof cannot restart itself")
    void aTransitionInProgressAbsorbsTheHit() {
        // Prepare - mid-poof, which the hurt guards cover
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setPoofCounter(10);
        runtimeState.setQueuedMode(RACCOON);
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, positionWithVelocity(0, 0));

        // Execute
        player.getHurtBy(null);

        // Verify - the leaf's promotion still completes; the hit changed nothing
        assertThat(player.isHurtable()).isFalse();
        assertThat(runtimeState.getPoofCounter()).isEqualTo(10);
        assertThat(runtimeState.getQueuedMode()).isEqualTo(RACCOON);
        assertThat(runtimeState.getHurtInvincibilityCounter()).isZero();
    }

    @Test
    @DisplayName("Growing is also queued, so the two transitions cannot be told apart by their counter alone")
    void bothTransitionsRecordTheirDestination() {
        // Two transitions share the poof counter and a third the grow counter; the destination has to
        // come from the queued mode, which is what tickModeTransition now reads.
        // Prepare
        final PlayerRuntimeState growing = new PlayerRuntimeState();
        final PlayerRuntimeState promoted = new PlayerRuntimeState();
        final PlayerRuntimeState demoted = new PlayerRuntimeState();

        // Execute
        hurtablePlayer(SHRUNK, growing, positionWithVelocity(0, 0)).turnToNormal();
        hurtablePlayer(NORMAL, promoted, positionWithVelocity(0, 0)).turnToRaccoon();
        hurtablePlayer(TANOOKI, demoted, positionWithVelocity(0, 0)).getHurtBy(null);

        // Verify
        assertThat(growing.getGrowShrinkCounter()).isEqualTo(NORMAL_TRANSITION_TICKS);
        assertThat(growing.getQueuedMode()).isEqualTo(NORMAL);
        assertThat(promoted.getQueuedMode()).isEqualTo(RACCOON);
        assertThat(demoted.getQueuedMode()).isEqualTo(NORMAL);
        assertThat(promoted.getPoofCounter())
            .as("the two poofs are indistinguishable by counter")
            .isEqualTo(demoted.getPoofCounter());
    }

    @Test
    @DisplayName("The invincibility is held at full length for the whole poof, then the player is Normal")
    void invincibilityOnlyStartsOnceThePoofIsOver() {
        // This is what makes arming the counter at hurt time equivalent to arming it when the poof ends:
        // the transition tick advances the poof and nothing else: while the cloud plays, the player is not
        // drawn, so the flash never counts down.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, positionWithVelocity(0, 0));
        doCallRealMethod().when(player).tickModeTransition();
        player.getHurtBy(null);

        // Execute - run the poof out
        for (int tick = 0; tick < SUIT_LOSS_TRANSITION_TICKS; tick++) {
            assertThat(runtimeState.isTransitioning()).as("still poofing at tick %d", tick).isTrue();
            assertThat(runtimeState.getHurtInvincibilityCounter())
                .as("untouched at tick %d", tick)
                .isEqualTo(HURT_INVINCIBILITY_TICKS);
            player.tickModeTransition();
        }

        // Verify
        assertThat(runtimeState.isTransitioning()).isFalse();
        assertThat(runtimeState.getHurtInvincibilityCounter()).isEqualTo(HURT_INVINCIBILITY_TICKS);
        verify(player).onTransitionComplete(NORMAL);
    }

    @Test
    @DisplayName("The leaf's promotion still lands on Raccoon, not on the suit loss's destination")
    void thePromotionStillCompletesIntoRaccoon() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = hurtablePlayer(NORMAL, runtimeState, positionWithVelocity(0, 0));
        doCallRealMethod().when(player).tickModeTransition();
        player.turnToRaccoon();

        // Execute
        for (int tick = 0; tick < SUIT_LOSS_TRANSITION_TICKS; tick++) {
            player.tickModeTransition();
        }

        // Verify
        verify(player).onTransitionComplete(RACCOON);
        assertThat(runtimeState.getHurtInvincibilityCounter())
            .as("gaining a suit grants no invincibility")
            .isZero();
    }

    @Test
    @DisplayName("A hit taken at a run leaves the run intact all the way through the poof")
    void momentumSurvivesTheHitAndThePoof() {
        // Getting hurt changes the suit, the poof, the flashing invincibility and the facing, and
        // nothing else — notably not the horizontal velocity, which only dying zeroes. So the speed the player
        // was carrying is the speed they come out of the poof with, and the P-meter keeps charging
        // because the run flag is recomputed from that velocity before anything reads it.
        // Prepare - running flat out
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        runtimeState.setRunning(true);
        final PlayerPosition position = positionWithVelocity(3.5, 0);
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, position);
        doCallRealMethod().when(player).tickModeTransition();

        // Execute
        player.getHurtBy(null);
        assertThat(position.getDX())
            .as("the hit itself must not brake the player")
            .isEqualTo(3.5);
        for (int tick = 0; tick < SUIT_LOSS_TRANSITION_TICKS; tick++) {
            player.tickModeTransition();
        }

        // Verify
        assertThat(position.getDX())
            .as("and neither does the poof, which advances nothing but its own counter")
            .isEqualTo(3.5);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A player whose hurt and transition entry points run for real against the given state. */
    private static LevelScenePlayer hurtablePlayer(
        final PlayerMode mode,
        final PlayerRuntimeState runtimeState,
        final PlayerPosition position
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getMode()).thenReturn(mode);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(position);
        // The transition's completion discards the B press held across it; see
        // LevelScenePlayerCapabilities#completeTransition.
        when(player.getInputHandler()).thenReturn(mock(PlayerInputHandler.class));
        doCallRealMethod().when(player).isAdvanced();
        doCallRealMethod().when(player).isLarge();
        doCallRealMethod().when(player).isHurtable();
        doCallRealMethod().when(player).getHurtBy(null);
        doCallRealMethod().when(player).die();
        doCallRealMethod().when(player).loseAdvancedSuit();
        doCallRealMethod().when(player).turnToShrunk();
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
