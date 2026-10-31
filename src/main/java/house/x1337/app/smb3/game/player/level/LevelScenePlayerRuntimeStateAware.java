package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerDeathKind;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_PAUSE_TICKS;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_RISE_YVEL;
import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VEL_THRESHOLD;
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
     * Awards the current kill-chain rung, then advances the chain (dasm prg000 @ PRG000_D2E1:
     * {@code LDA Kill_Tally / INC Kill_Tally / JSR Score_Get100PlusPts}) - the score is read
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
        // Probe at horizontal center (X+8) only — matching dasm PRG008_A77E which
        // checks a single fixed point above the player's head. Adding a right-edge
        // probe (X+14) at the same heightOffset falsely detects a normal rightward
        // wall collision as low-clearance, causing the player to slide through walls.
        final boolean tileAbove = collisionGrid.collidesAtOffset(this, Offset.of(8, heightOffset)) &&
            !collisionGrid.isOneWayTileFromPlayer(this, 8, heightOffset);
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
     *                     frame (dasm prg008 PRG008_B4F3: INC Player_WalkAnimTicks
     *                     on wall hit keeps the walk animation running)
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
                // dasm prg008: Player_FlyTime > 0 means the player is in
                // powered flight mode (raccoon/tanooki). The animation system
                // (Player_AnimTailWag) selects flying frames whenever FlyTime
                // is nonzero, independent of WagCount. WagCount only controls
                // the velocity cap physics, not the logical flight state.
                //
                // Small Mario also receives FlyTime on full-P launch (the dasm
                // grants it to all suits before the ability check), but it has
                // no flight Y-effects and uses JUMPING/FALLING states with the
                // PF_FASTJUMPFALLSMALL visual frame instead of FLYING.
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
                // a direction is held (dasm: WalkAnimTicks advances via
                // Player_GroundHControl which reads Pad_Holding).
                runtimeState.setTo(WALKING);
            } else if (hitSomething && (inputLeft || inputRight)) {
                // dasm prg008 PRG008_B4F3: when the player hits a wall while
                // pressing a direction, Player_WalkAnimTicks is incremented
                // which keeps the walk animation cycling. The player appears to
                // "walk in place" against the wall rather than going still.
                runtimeState.setTo(WALKING);
            } else if (abs(position.getDX()) < 0.01) {
                runtimeState.setTo(STILL);
            } else if (runtimeState.isRunning() && abs(position.getDX()) >= PLAYER_SPREAD_EAGLE_THRESHOLD) {
                // Spread-eagle: abs(XVel) >= $37 in the original (prg008.asm
                // Player_SetSpecialFrames). Full P-meter speed reached.
                runtimeState.setTo(POWER_RUNNING);
            } else if (runtimeState.isRunning()) {
                // B held, speed >= TOPRUNSPEED but below spread-eagle threshold.
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
        if (abs(dx) < PLAYER_SKID_VEL_THRESHOLD) {
            return false;
        }
        // Pressing opposite direction from current movement
        return (dx > 0 && inputLeft) || (dx < 0 && inputRight);
    }

    /** Begins the small→Super grow transition (dasm {@code Player_Grow = $2f}). */
    default void turnToNormal() {
        getRuntimeState().setQueuedMode(NORMAL);
        getRuntimeState().setGrowShrinkCounter(NORMAL_TRANSITION_TICKS);
        neutraliseMotionForTransition();
    }

    /** Begins the large→Raccoon poof transition (dasm {@code Player_SuitLost = $17}). */
    default void turnToRaccoon() {
        getRuntimeState().setQueuedMode(RACCOON);
        getRuntimeState().setPoofCounter(RACCOON_TRANSITION_TICKS);
        neutraliseMotionForTransition();
    }

    /**
     * Begins the Normal→Shrunk shrink a damaging hit causes (dasm {@code Player_GetHurt} @ PRG000_DA4E
     * and PRG000_DA6D: {@code Player_QueueSuit = $02} then {@code DEC} to small, {@code Player_Grow =
     * $2f}, {@code Player_FlashInv = $71}).
     *
     * <p>It rides the same {@code Player_Grow} counter and the same frame table as the small→Super grow,
     * only played in reverse: {@code Player_Update} applies the queued suit at frame start, so the suit
     * is already small for the whole transition, and the draw routine reverses the frame index when the
     * suit is small (prg029 @ PRG029_D22E: {@code X = $0B - (Player_Grow >> 2)}). The shrink is therefore
     * literally the grow animation backwards — rendered by its own {@code normalToShrunkAnimator}, which
     * shares {@link PlayerRuntimeState#NORMAL_TRANSITION_TICKS} and the grow art with {@link #turnToNormal}
     * but is keyed apart from it by the queued mode.
     *
     * <p>Unlike the poof paths ({@link #loseAdvancedSuit}), the flashing invincibility armed here
     * <em>runs during the shrink itself</em>, so the player visibly flickers while shrinking. The grow
     * counter does not blank the sprite the way the poof cloud does: the ROM falls through to
     * {@code JSR Player_Draw} every grow tick (prg029 @ PRG029_D224), and {@code Player_Draw} is where
     * {@code DEC Player_FlashInv} and the blank-phase test live. See {@code tickModeTransition}, which
     * decrements the flash on the grow path and holds it on the poof path for exactly this reason.
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
        position.setDY(PLAYER_DEATH_RISE_YVEL);
        announceDeath(THROE);
    }

    /**
     * Kills the player for falling into a pit (dasm prg008 @ PRG008_A3FA, lines 847-863): {@code Player_Die},
     * then {@code Event_Countdown = $C0} and {@code Player_IsDying = 2}, which "jumps the initial part of the
     * death sequence". So there is no pause, no pop-up and no fall: {@code Player_Die_FellOff} (prg029) moves
     * nothing, draws nothing, and only waits for the countdown, which here is the death music's end
     * ({@code PlayerDeathSequence}).
     *
     * <p>It deliberately skips {@link #isHurtable()}. The ROM's pit check reads neither
     * {@code Player_FlashInv} nor the suit, and sets the suit to small first, so a flashing, large or
     * Raccoon player dies here as well. Like {@code Player_Die} it clears the flashing invincibility and
     * stops the player; {@code Player_YVel = -64} is written too, but the fell-off routine never applies it,
     * so the port leaves the player still. The suit is not reset here because the restart respawns the
     * player small anyway.
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
        return !runtimeState.isHurtInvincible() && !runtimeState.isTransitioning() && !runtimeState.isDying();
    }

    /**
     * @return whether the player has something in their hands (dasm {@code Player_IsHolding})
     */
    default boolean isCarrying() {
        return getRuntimeState().isCarrying();
    }

    /**
     * Whether a carriable object touching the player right now may be taken — the ROM's
     * {@code Object_HoldKickOrHurtPlayer} guard (dasm prg000 @ PRG000_D343:
     * {@code LDA Player_ISHolding_OLD / BNE} straight to {@code RTS}).
     *
     * <p>Two conjuncts, and they are not the same test. {@code wasCarrying} is the ROM's one-tick lag:
     * the drop runs in the object loop, this runs afterwards in the player collision pass, so without
     * the lag the very frame that let go of one shell could take the next. {@code isCarrying()} covers
     * what the ROM gets from having eight object slots and one flag — two carriables overlapping the
     * player on the <em>same</em> collision pass. The ROM would put both in {@code OBJSTATE_HELD} and
     * carry only the one it happened to draw; a single reference here would strand the other in a state
     * nothing ever leaves.
     *
     * @return {@code true} if a pickup is allowed on this tick
     */
    default boolean canPickUpCarriable() {
        return !getRuntimeState().isWasCarrying() && !isCarrying();
    }

    /**
     * Takes an object into the player's hands (dasm prg000 @ PRG000_D34F:
     * {@code LDA #OBJSTATE_HELD / STA Objects_State}).
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
     * Snapshots the carry state for the ROM's {@code Player_ISHolding_OLD}.
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
     * invincibility (dasm {@code Player_GetHurt} @ PRG000_DA15 and PRG000_DA6D:
     * {@code Player_SuitLost = $17}, {@code Player_QueueSuit = $02}, {@code Player_FlashInv = $71}).
     *
     * <p>The invincibility counter is armed here, at the same moment as the poof, rather than when the
     * poof ends — the ROM writes both in the same breath. It still does not start <em>running</em> until
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
     * <p><b>Horizontal momentum and the P-meter are preserved.</b> No transition in the ROM writes
     * {@code Player_XVel} or {@code Player_Power}: {@code ObjHit_PUpMush} (dasm prg001 @ PRG001_A8AB)
     * writes only {@code Player_QueueSuit} and {@code Player_Grow}, {@code ObjHit_SuperLeaf} only the
     * queued suit and {@code Player_SuitLost}, and {@code Player_GetHurt} (prg000 @ PRG000_DA15) only
     * those plus {@code Player_FlashInv} and {@code Player_Flip}. The one routine that does stop the
     * player dead is {@code Player_Die}, which zeroes {@code Player_XVel} explicitly — the contrast is
     * the point, and it is why a hit taken at a run leaves the run intact.
     *
     * <p>Zeroing it here cost the player their speed <em>and</em> their P-meter: with {@code DX} back at
     * zero, the first resumed frame fails the run test in {@code handlePowerMeterAndRunFlag}
     * ({@code abs(DX) >= PLAYER_TOPRUNSPEED}), so the meter starts draining instead of charging and the
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
     * keeps the pass-through exemption intact and lets the jump resume where it left off — which is also
     * what the ROM does, {@code Player_Grow} being consumed purely as a draw-time frame override
     * (prg029 PRG029_D224).
     *
     * <p>What is still spent are the flight abilities the transition itself ends — flight time and tail
     * wag — since a player mid-transition is either gaining a suit that has not earned them yet or losing
     * the one that did.
     *
     * <p><b>A tail swing in progress is deliberately left alone</b>, and this is load-bearing.
     * {@code Player_GetHurt} (dasm prg000 @ PRG000_D9D3 through PRG000_DA6D) writes
     * {@code Player_SuitLost}, {@code Player_QueueSuit}, {@code Player_Kuribo}, {@code Player_Grow},
     * {@code Player_FlashInv} and {@code Player_Flip} — and never {@code Player_TailAttack}. In the whole
     * ROM that counter is only cleared by kicking a shell (prg008 @ PRG008_AF52) and by grabbing an ice
     * block (@ PRG008_B5F4).
     *
     * <p>It matters because of <em>when</em> the hurt lands relative to the strike: the ROM resolves both
     * inside the object loop, hurt first ({@code Player_HitEnemy}, reached from the end of the enemy's own
     * handler) and the tail immediately after ({@code Object_HitByTailOrBouncer}) — see
     * {@code ActiveObjectGrid.resolveActiveObjectCollisions}, which keeps that order. Zeroing the counter
     * here therefore did not merely tidy up a spent ability: it disarmed, retroactively, a strike the same
     * tick was about to resolve, which is exactly how the original lets a player kill the enemy that just
     * hurt him. With the counter untouched, the hit costs the suit and the swing still connects.
     *
     * <p>Leaving it running past the poof is safe because the counter is ticked <b>unconditionally</b>,
     * every frame, halt or no halt and tail or no tail ({@code tickTailAttackCountdown}, the ROM's shared
     * counter block) — so it always reaches zero on its own a few frames later. It is not safe to leave
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
