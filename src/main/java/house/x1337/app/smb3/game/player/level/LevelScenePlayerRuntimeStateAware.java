package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VEL_THRESHOLD;
import static house.x1337.app.smb3.GameConstants.PLAYER_SPREAD_EAGLE_THRESHOLD;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
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
    PlayerRuntimeState getRuntimeState();

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
     * @param inputHandler
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
        getRuntimeState().setGrowCounter(NORMAL_TRANSITION_TICKS);
        neutraliseMotionForTransition();
    }

    /** Begins the large→Raccoon poof transition (dasm {@code Player_SuitLost = $17}). */
    default void turnToRaccoon() {
        getRuntimeState().setQueuedMode(RACCOON);
        getRuntimeState().setPoofCounter(RACCOON_TRANSITION_TICKS);
        neutraliseMotionForTransition();
    }

    default void onHurt() {
        if (!isHurtable() || !isAdvanced()) {
            return;
        }
        loseAdvancedSuit();
    }

    default boolean isHurtable() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        return !runtimeState.isHurtInvincible() && !runtimeState.isTransitioning();
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
     * <p>What is still spent are the abilities the transition itself ends — flight, tail wag and any
     * swing in progress — since a player mid-transition is either gaining a suit that has not earned
     * them yet or losing the one that did.
     */
    private void neutraliseMotionForTransition() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        runtimeState.standUp();
        runtimeState.setPlayerFlyTime(0);
        runtimeState.setPlayerWagCount(0);
        runtimeState.setPlayerTailAttackCountdown(0);
        if (runtimeState.isInAir()) {
            // Mid-air: the jump is still in progress and must survive the freeze intact.
            return;
        }
        runtimeState.setTo(STILL);
        position.setDY(0);
    }
}
