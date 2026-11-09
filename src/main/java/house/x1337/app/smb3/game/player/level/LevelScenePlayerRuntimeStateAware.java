package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerDeathKind;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_PAUSE_TICKS;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_RISE_Y_VELOCITY;
import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VELOCITY_THRESHOLD;
import static house.x1337.app.smb3.GameConstants.PLAYER_SPREAD_EAGLE_THRESHOLD;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.THROE;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.game.object.level.KillTallyAware.KILL_TALLY_LADDER;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.HURT_INVINCIBILITY_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.NORMAL_TRANSITION_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.RACCOON_TRANSITION_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.SUIT_LOSS_TRANSITION_TICKS;
import static java.lang.Math.abs;

public interface LevelScenePlayerRuntimeStateAware
    extends
        LevelScenePlayerOrientationAware,
        LevelScenePlayerPositionAware,
        Player {
    void announceDeath(PlayerDeathKind kind);
    LevelScenePlayer asLevelScenePlayer();
    PlayerRuntimeState getRuntimeState();

    /**
     * Awards the current kill-chain rung, then advances the chain. The score is read
     * <em>before</em> the increment, so the first stomp of a chain pays {@code SCORE_100}.
     */
    default Reward nextKillChainReward() {
        return KILL_TALLY_LADDER[getRuntimeState().nextKillTally()];
    }

    default Reward currentKillChain() {
        return getRuntimeState().resolveKillReward();
    }

    default int determineHeightOffset() {
        return (isSmall() || getRuntimeState().isDucking()) ? 20 : 10;
    }

    default boolean isLowClearance(
        final StaticEnvironmentCollisionGrid collisionGrid,
        final int heightOffset
    ) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        // Probe at horizontal center (X+8) only: a single fixed point above the player's head. Adding a right-edge
        // probe (X+14) at the same heightOffset falsely detects a normal rightward
        // wall collision as low-clearance, causing the player to slide through walls.
        final Coordinates probe = of(8, heightOffset);
        final boolean tileAbove = collisionGrid.collidesAtOffset(this, probe) &&
            !collisionGrid.isOneWayTileFromPlayer(this, probe);
        return tileAbove && !runtimeState.isInAir();
    }

    /**
     * Refines the player state after collision resolution. Ground states are
     * determined by velocity; air states are set during jump initiation and
     * collision (landing / walking off ledge).
     *
     * <p>Ducking is now a separate flag on {@code PlayerRuntimeState} and does
     * not participate in the movement state machine. The animator is
     * responsible for rendering the duck frame when the flag is set.
     *
     * @param inputHandler the input handler to read the current pad state from
     * @param hitSomething true if the player collided with a horizontal wall this
     *                     frame (a wall hit keeps the walk animation running)
     * @param lowClearance true if the player is in low-clearance slide mode
     */
    default void refinePlayerState(
        final PlayerInputHandler inputHandler,
        final boolean hitSomething,
        final boolean lowClearance
    ) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();

        if (runtimeState.isInAir()) {
            if (runtimeState.getPlayerFlyTime() > 0 && hasTail()) {
                // Remaining flight time > 0 means the player is in powered
                // flight mode (raccoon/tanooki). The animation selects flying
                // frames whenever the flight time is nonzero, independent of
                // WagCount. WagCount only controls the velocity cap physics,
                // not the logical flight state.
                //
                // Small Mario also receives flight time on full-P launch (it is
                // granted to all suits before the ability check), but it has
                // no flight Y-effects and uses JUMPING/FALLING states with the
                // fast jump/fall visual frame instead of FLYING.
                runtimeState.setTo(FLYING);
            } else if (position.getDY() < 0) {
                runtimeState.setTo(JUMPING);
            } else {
                runtimeState.setTo(FALLING);
            }
        } else {
            final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
            final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
            final boolean inputLeft = rawLeft && !rawRight;
            final boolean inputRight = rawRight && !rawLeft;
            if (isCurrentlySkidding(inputLeft, inputRight)) {
                runtimeState.setTo(SKIDDING);
            } else if (lowClearance && (inputLeft || inputRight)) {
                // During low clearance slide, show walk animation only when
                // a direction is held.
                runtimeState.setTo(WALKING);
            } else if (hitSomething && (inputLeft || inputRight)) {
                // When the player hits a wall while pressing a direction, the
                // walk animation keeps cycling. The player appears to
                // "walk in place" against the wall rather than going still.
                runtimeState.setTo(WALKING);
            } else if (abs(position.getDX()) < 0.01) {
                runtimeState.setTo(STILL);
            } else if (runtimeState.isRunning() && abs(position.getDX()) >= PLAYER_SPREAD_EAGLE_THRESHOLD) {
                // Spread-eagle: abs(DX) >= PLAYER_SPREAD_EAGLE_THRESHOLD.
                // Full P-meter speed reached.
                runtimeState.setTo(POWER_RUNNING);
            } else if (runtimeState.isRunning()) {
                // B held, speed >= top run speed but below spread-eagle threshold.
                // Still uses walk animation frames (accelerating toward max).
                runtimeState.setTo(RUNNING);
            } else {
                runtimeState.setTo(WALKING);
            }
        }
    }

    default boolean isCurrentlySkidding(
        final boolean inputLeft,
        final boolean inputRight
    ) {
        if (getRuntimeState().isInAir()) {
            return false;
        }
        final double dx = getPosition().getDX();
        if (abs(dx) < PLAYER_SKID_VELOCITY_THRESHOLD) {
            return false;
        }
        // Pressing opposite direction from current movement
        return (dx > 0 && inputLeft) || (dx < 0 && inputRight);
    }

    /** Begins the small→Super grow transition ({@link PlayerRuntimeState#NORMAL_TRANSITION_TICKS} ticks). */
    default void turnToNormal() {
        getRuntimeState().setQueuedMode(NORMAL);
        getRuntimeState().setGrowShrinkCounter(NORMAL_TRANSITION_TICKS);
        neutraliseMotionForTransition();
    }

    /** Begins the large→Raccoon poof transition ({@link PlayerRuntimeState#RACCOON_TRANSITION_TICKS} ticks). */
    default void turnToRaccoon() {
        getRuntimeState().setQueuedMode(RACCOON);
        getRuntimeState().setPoofCounter(RACCOON_TRANSITION_TICKS);
        neutraliseMotionForTransition();
    }

    /**
     * Begins the Normal→Shrunk shrink a damaging hit causes: the suit is queued as small, the grow counter is
     * set to {@link PlayerRuntimeState#NORMAL_TRANSITION_TICKS} and the flashing invincibility to
     * {@link PlayerRuntimeState#HURT_INVINCIBILITY_TICKS}.
     *
     * <p>It rides the same grow counter and the same frame table as the small→Super grow, only played in
     * reverse: the queued suit is applied at the start of the frame, so the suit is already small for the
     * whole transition, and the frame index is reversed when the suit is small ({@code 11 - (counter >> 2)}).
     * The shrink is therefore literally the grow animation backwards — rendered by its own
     * {@code normalToShrunkAnimator}, which shares {@link PlayerRuntimeState#NORMAL_TRANSITION_TICKS} and the
     * grow art with {@link #turnToNormal} but is keyed apart from it by the queued mode.
     *
     * <p>Unlike the poof paths ({@link #loseAdvancedSuit}), the flashing invincibility armed here
     * <em>runs during the shrink itself</em>, so the player visibly flickers while shrinking. The grow
     * counter does not blank the sprite the way the poof cloud does: the player is still drawn on every grow
     * tick, and drawing the player is where the flash counts down and its blank phase is tested. See
     * {@code tickModeTransition}, which decrements the flash on the grow path and holds it on the poof path
     * for exactly this reason.
     */
    default void turnToShrunk() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        runtimeState.setQueuedMode(SHRUNK);
        runtimeState.setGrowShrinkCounter(NORMAL_TRANSITION_TICKS);
        runtimeState.setHurtInvincibilityCounter(HURT_INVINCIBILITY_TICKS);
        neutraliseMotionForTransition();
    }

    default void getHurtBy(final ActiveLevelObject activeLevelObject) {
        if (!isHurtable()) {
            return;
        }
        if (isAdvanced()) {
            loseAdvancedSuit();
        } else if (isLarge()) {
            turnToShrunk();
        } else {
            die();
        }
    }

    default void die() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (runtimeState.isDying()) {
            return;
        }
        final PlayerPosition position = getPosition();
        runtimeState.startDying();
        runtimeState.setDeathPauseCounter(PLAYER_DEATH_PAUSE_TICKS);
        runtimeState.standUp();
        runtimeState.setPlayerFlyTime(0);
        runtimeState.setPlayerWagCount(0);
        runtimeState.setPlayerTailAttackCountdown(0);
        position.setDX(0);
        position.setDY(PLAYER_DEATH_RISE_Y_VELOCITY);
        announceDeath(THROE);
    }

    /**
     * Kills the player for falling into a pit. The opening part of the death sequence is skipped, so there is
     * no pause, no pop-up and no fall: the fell-off death moves nothing, draws nothing, and only waits for the
     * countdown, which here is the death music's end ({@code PlayerDeathSequence}).
     *
     * <p>It deliberately skips {@link #isHurtable()}. The pit check reads neither the flashing invincibility
     * nor the suit, so a flashing, large or Raccoon player dies here as well. It clears the flashing
     * invincibility and stops the player; unlike {@link #die()} it gives the player no rise velocity, so the
     * player stays still. The suit is not set to small here because the restart respawns the player small
     * anyway.
     *
     * <p>A player who is already dying is left alone, so one death is announced once.
     */
    default void dieByFallingOff() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (runtimeState.isDying()) {
            return;
        }
        final PlayerPosition position = getPosition();
        runtimeState.startFallingOffDeath();
        runtimeState.standUp();
        runtimeState.setPlayerFlyTime(0);
        runtimeState.setPlayerWagCount(0);
        runtimeState.setPlayerTailAttackCountdown(0);
        runtimeState.setHurtInvincibilityCounter(0);
        position.setDX(0);
        position.setDY(0);
        announceDeath(FELL_OFF);
    }

    default boolean isHurtable() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        return !runtimeState.isHurtInvincible()
            && !runtimeState.isTransitioning()
            && !runtimeState.isDying()
            && !runtimeState.isInPipe();
    }

    /**
     * @return whether the player has something in their hands
     */
    default boolean isCarrying() {
        return getRuntimeState().isCarrying();
    }

    /**
     * Whether a carriable object touching the player right now may be taken. A player who held something on
     * the previous tick cannot pick anything up.
     *
     * <p>Two conjuncts, and they are not the same test. {@code wasCarrying} is a one-tick lag:
     * the drop runs in the object loop, this runs afterwards in the player collision pass, so without
     * the lag the very frame that let go of one shell could take the next. {@code isCarrying()} covers
     * two carriables overlapping the player on the <em>same</em> collision pass: the lag flag alone would let
     * both be taken, and since a single reference holds only one, the other would be stranded in a state
     * nothing ever leaves.
     *
     * @return {@code true} if a pickup is allowed on this tick
     */
    default boolean canPickUpCarriable() {
        return !getRuntimeState().isWasCarrying() && !isCarrying();
    }

    /**
     * Takes an object into the player's hands and puts the object into its held state.
     *
     * @param carriable the object being picked up
     */
    default void pickUp(final CarriableLevelObject carriable) {
        getRuntimeState().setCarriedObject(carriable);
        carriable.onCarried(asLevelScenePlayer());
    }

    default void releaseCarriedObject() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final CarriableLevelObject carried = runtimeState.getCarriedObject();
        if (carried == null) {
            return;
        }
        runtimeState.setCarriedObject(null);
        asLevelScenePlayer().kickCarriedObject(carried);
    }

    /**
     * Snapshots the carry state for the one-tick pickup lag ({@code wasCarrying}).
     *
     * <p>Advanced at the very top of the player's tick, above every early return, for the same reason
     * {@code tickTailAttackCountdown} is: a lag flag that stops being updated is a lag flag that lies.
     * A player who dies holding a shell must not come back able to pick one up on the frame the carry
     * ended.
     */
    default void advanceCarryLag() {
        getRuntimeState().setWasCarrying(isCarrying());
    }

    /**
     * Discards an advanced suit, poofing back to {@link PlayerMode#NORMAL} and arming the flashing
     * invincibility: the poof runs for {@link PlayerRuntimeState#SUIT_LOSS_TRANSITION_TICKS} ticks and the
     * invincibility for {@link PlayerRuntimeState#HURT_INVINCIBILITY_TICKS}.
     *
     * <p>The invincibility counter is armed here, at the same moment as the poof, rather than when the
     * poof ends — both are set together. It still does not start <em>running</em> until
     * gameplay resumes, because nothing decrements it while the poof owns the draw routine; see
     * {@link PlayerRuntimeState#getHurtInvincibilityCounter()}.
     */
    default void loseAdvancedSuit() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        runtimeState.setQueuedMode(NORMAL);
        runtimeState.setPoofCounter(SUIT_LOSS_TRANSITION_TICKS);
        runtimeState.setHurtInvincibilityCounter(HURT_INVINCIBILITY_TICKS);
        neutraliseMotionForTransition();
    }

    /**
     * Spends the abilities a transition ends and settles a grounded player, without touching what the
     * player had built up on the way in.
     *
     * <p><b>Horizontal momentum and the P-meter are preserved.</b> No transition touches the horizontal
     * velocity or the P-meter: collecting a mushroom only queues the suit and starts the grow counter,
     * collecting a leaf only queues the suit and starts the poof, and getting hurt only does those plus
     * arming the flashing invincibility and setting the facing. The one event that does stop the player
     * dead is dying, which zeroes the horizontal velocity explicitly — the contrast is the point, and it is
     * why a hit taken at a run leaves the run intact.
     *
     * <p>Zeroing it here cost the player their speed <em>and</em> their P-meter: with {@code DX} back at
     * zero, the first resumed frame fails the run test in {@code handlePowerMeterAndRunFlag}
     * ({@code abs(DX) >= PLAYER_TOP_RUN_SPEED}), so the meter starts draining instead of charging and the
     * player has to accelerate from a standstill. Preserving {@code DX} keeps the run flag set on that
     * very frame, because the flag is recomputed from velocity before anything else reads it.
     *
     * <p><b>An airborne player keeps its vertical physics.</b> The transition freezes the player for its
     * whole duration ({@code isHaltingGameplay} → {@code tickModeTransition}), and the movement mode is
     * what tells collision whether the player is airborne. Forcing {@code STILL} on a
     * player who grew mid-jump therefore claims they are <em>standing</em> in mid-air, and nothing
     * restores the airborne state when the counter runs out — so the first resumed frame resolves them
     * as grounded. That is how a mushroom grabbed while rising through a one-way platform left the
     * player standing inside it: {@code isSolidVert} only exempts a one-way platform while the player is
     * rising ({@code DY < 0}), so a zeroed {@code DY} plus a grounded state turned the platform into a
     * floor the player could walk along from underneath. Preserving the airborne mode and {@code DY}
     * keeps the pass-through exemption intact and lets the jump resume where it left off, the grow counter
     * being consumed purely as a draw-time frame override.
     *
     * <p>What is still spent are the flight abilities the transition itself ends — flight time and tail
     * wag — since a player mid-transition is either gaining a suit that has not earned them yet or losing
     * the one that did.
     *
     * <p><b>A tail swing in progress is deliberately left alone</b>, and this is load-bearing. Getting hurt
     * changes the suit, the grow and poof counters, the flashing invincibility and the facing, but never the
     * tail-attack countdown. That countdown is only cleared by kicking a shell and by grabbing an ice block.
     *
     * <p>It matters because of <em>when</em> the hurt lands relative to the strike: both are resolved
     * inside the object loop, hurt first (reached from the end of the enemy's own handler) and the tail
     * immediately after — see {@code ActiveObjectGrid.resolveActiveObjectCollisions}, which keeps that
     * order. Zeroing the counter here therefore did not merely tidy up a spent ability: it disarmed,
     * retroactively, a strike the same tick was about to resolve, which is exactly how a player gets to kill
     * the enemy that just hurt him. With the counter untouched, the hit costs the suit and the swing still
     * connects.
     *
     * <p>Leaving it running past the poof is safe because the counter is ticked <b>unconditionally</b>,
     * every frame, halt or no halt and tail or no tail ({@code tickTailAttackCountdown}, alongside the other
     * shared countdowns) — so it always reaches zero on its own a few frames later. It is not safe to leave
     * running <em>and</em> gate the tick: the swing would never end, and the facing freeze that belongs to
     * it would never lift.
     */
    private void neutraliseMotionForTransition() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        runtimeState.standUp();
        runtimeState.setPlayerFlyTime(0);
        runtimeState.setPlayerWagCount(0);
        if (runtimeState.isInAir()) {
            // Mid-air: the jump is still in progress and must survive the freeze intact.
            return;
        }
        runtimeState.setTo(STILL);
        position.setDY(0);
    }
}
