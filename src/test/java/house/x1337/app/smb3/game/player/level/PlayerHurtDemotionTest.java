package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerMode;
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
 * Pins the advanced→Normal suit loss a damaging hit causes (dasm {@code Player_GetHurt} @ PRG000_D9D3).
 *
 * <p>Three things have to happen together, and the ROM writes all three in the same breath: the shared
 * {@code Player_SuitLost} poof starts, {@code Player_QueueSuit} names Normal as where it lands, and
 * {@code Player_FlashInv} is armed. Arming it here rather than when the poof ends is safe precisely
 * because nothing decrements it while the poof is on screen — see {@link HurtInvincibilityFlickerTest}.
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
        player.onHurt();

        // Verify
        assertThat(runtimeState.getPoofCounter())
            .as("the same $17 poof the Super Leaf plays")
            .isEqualTo(SUIT_LOSS_TRANSITION_TICKS);
        assertThat(runtimeState.getQueuedMode())
            .as("Player_QueueSuit = $02, i.e. back to plain Super")
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
        // The ROM shrinks Super here (PRG000_DA4E): Player_QueueSuit = $02 then DEC to small,
        // Player_Grow = $2f (the same counter the mushroom grow uses, played backwards), and
        // Player_FlashInv = $71. Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        final LevelScenePlayer player = hurtablePlayer(NORMAL, runtimeState, positionWithVelocity(3.0, 0));

        // Execute
        player.onHurt();

        // Verify
        assertThat(runtimeState.getGrowShrinkCounter())
            .as("the same $2f counter the small→Super grow runs")
            .isEqualTo(NORMAL_TRANSITION_TICKS);
        assertThat(runtimeState.getPoofCounter()).as("a shrink is not a poof").isZero();
        assertThat(runtimeState.getQueuedMode())
            .as("Player_QueueSuit = $02 then DEC, i.e. down to small")
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
        // A grow/shrink falls through to JSR Player_Draw every tick (prg029 @ PRG029_D224), and that is
        // where DEC Player_FlashInv lives — so unlike the poof, the flash runs during the transition and
        // the player visibly flickers while shrinking. Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(RUNNING);
        final LevelScenePlayer player = hurtablePlayer(NORMAL, runtimeState, positionWithVelocity(0, 0));
        doCallRealMethod().when(player).tickModeTransition();
        player.onHurt();

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
    @DisplayName("A small player absorbs the hit untouched — death is not implemented, so it is a no-op")
    void smallPlayerIsUnaffected() {
        // The ROM kills small here (Player_Die); that is not implemented, so the hit must be a no-op
        // rather than silently shrinking a player who should die or granting free invincibility.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = hurtablePlayer(SHRUNK, runtimeState, positionWithVelocity(0, 0));

        // Execute
        player.onHurt();

        // Verify
        assertThat(runtimeState.getPoofCounter()).isZero();
        assertThat(runtimeState.getGrowShrinkCounter()).isZero();
        assertThat(runtimeState.getHurtInvincibilityCounter()).isZero();
        assertThat(runtimeState.getQueuedMode()).isNull();
    }

    @Test
    @DisplayName("A hit lands only once: the invincibility it leaves behind absorbs the next one")
    void invincibilityAbsorbsTheNextHit() {
        // Prepare - already flashing from an earlier hit, and back in one piece
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setHurtInvincibilityCounter(40);
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, positionWithVelocity(0, 0));

        // Execute
        player.onHurt();

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
        // Prepare - mid-poof, as the ROM's Player_SuitLost / Player_HaltGame guards cover
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setPoofCounter(10);
        runtimeState.setQueuedMode(RACCOON);
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, positionWithVelocity(0, 0));

        // Execute
        player.onHurt();

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
        hurtablePlayer(TANOOKI, demoted, positionWithVelocity(0, 0)).onHurt();

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
        // the transition tick advances the poof and nothing else, exactly as the ROM's draw dispatcher
        // does when it plays the cloud and returns before ever reaching Player_Draw's DEC.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = hurtablePlayer(RACCOON, runtimeState, positionWithVelocity(0, 0));
        doCallRealMethod().when(player).tickModeTransition();
        player.onHurt();

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
        // Player_GetHurt writes Player_SuitLost, Player_QueueSuit, Player_FlashInv and Player_Flip, and
        // nothing else — notably not Player_XVel, which only Player_Die zeroes. So the speed the player
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
        player.onHurt();
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
        doCallRealMethod().when(player).isAdvanced();
        doCallRealMethod().when(player).isLarge();
        doCallRealMethod().when(player).isHurtable();
        doCallRealMethod().when(player).onHurt();
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
