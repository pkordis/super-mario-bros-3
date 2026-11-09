package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.enumeration.PlayerDeathKind;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.KillTallyAware;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import lombok.Data;

import static house.x1337.app.smb3.enumeration.PipeEntryDirection.LEFT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.NONE;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.THROE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;

@Data
@Prototype
public class PlayerRuntimeState implements KillTallyAware {
    public static final int HURT_INVINCIBILITY_TICKS = 113;
    public static final int KICK_POSE_TICKS = 13;
    public static final int NORMAL_TRANSITION_TICKS = 47;
    public static final int PIPE_ENTRY_TICKS = 60;
    public static final int PIPE_EXIT_TICKS_DOWN = 7;
    public static final int PIPE_EXIT_TICKS_SIDE = 32;
    public static final int PIPE_EXIT_TICKS_UP = 63;
    public static final int PIPE_SIDE_MOVE_MIN_COUNTDOWN = 16;
    public static final int RACCOON_TRANSITION_TICKS = 23;
    public static final int SUIT_LOSS_TRANSITION_TICKS = 23;
    public static final int TURN_AROUND_FACE_TICKS = 7;

    private static final int INVINCIBILITY_BLANK_PHASE_MASK = 2;

    private CarriableLevelObject carriedObject;
    private PipeEntryDirection pipeEntryDirection;
    private PipeEntryDirection pipeExitDirection;
    private PipePortal enteredPipePortal;
    private PipePortal exitingPipePortal;
    private PlayerDeathKind deathKind = NONE;
    private PlayerMode queuedMode;
    private PlayerMovement movement = STILL;
    private boolean ducking;
    private boolean hitCeiling;
    private boolean running;
    private boolean runningOffLevel;
    private boolean runOffHalted;
    private boolean wasCarrying;
    private int deathPauseCounter;
    private int flyTimeToggle;
    private int growShrinkCounter;
    private int hurtInvincibilityCounter;
    private int kickCountdown;
    private int killTally;
    private int lowClearanceGrace;
    private int pipeCountdown;
    private int playerFlyTime;
    private int playerTailAttackCountdown;
    private int playerWagCount;
    private int poofCounter;
    private int turnAroundFaceCounter;

    public boolean isInAir() {
        return movement == JUMPING || movement == FALLING || movement == FLYING;
    }

    public boolean isTransitioning() {
        return isChangingSize() || isTurningToRaccoon();
    }

    public void startDying() {
        deathKind = THROE;
    }

    public void startFallingOffDeath() {
        deathKind = FELL_OFF;
    }

    public boolean isDying() {
        return deathKind != NONE;
    }

    public boolean isDyingByFallingOff() {
        return deathKind == FELL_OFF;
    }

    public boolean isDeathPausing() {
        return deathKind == THROE && deathPauseCounter > 0;
    }

    public void decrementDeathPause() {
        if (deathPauseCounter > 0) {
            deathPauseCounter--;
        }
    }

    public void startEnteringPipe(
        final PipePortal portal,
        final PipeEntryDirection direction
    ) {
        enteredPipePortal = portal;
        pipeEntryDirection = direction;
        pipeCountdown = PIPE_ENTRY_TICKS;
    }

    public boolean isEnteringPipe() {
        return enteredPipePortal != null;
    }

    public void stopEnteringPipe() {
        enteredPipePortal = null;
        pipeEntryDirection = null;
        pipeCountdown = 0;
    }

    public void decrementPipeCountdown() {
        if (pipeCountdown > 0) {
            pipeCountdown--;
        }
    }

    public boolean isEnteringSidePipe() {
        return isEnteringPipe() && (pipeEntryDirection == LEFT || pipeEntryDirection == RIGHT);
    }

    public void startExitingPipe(
        final PipePortal portal,
        final PipeEntryDirection direction
    ) {
        enteredPipePortal = null;
        pipeEntryDirection = null;
        exitingPipePortal = portal;
        pipeExitDirection = direction;
        pipeCountdown = exitTicks(direction);
    }

    public void stopExitingPipe() {
        exitingPipePortal = null;
        pipeExitDirection = null;
        pipeCountdown = 0;
    }

    public boolean isExitingPipe() {
        return exitingPipePortal != null;
    }

    public boolean isExitingSidePipe() {
        return isExitingPipe() && (pipeExitDirection == LEFT || pipeExitDirection == RIGHT);
    }

    public boolean isInPipe() {
        return isEnteringPipe() || isExitingPipe();
    }

    public boolean isWalkingThroughSidePipe() {
        return isWalkingIntoSidePipe() || isExitingSidePipe();
    }

    private static int exitTicks(final PipeEntryDirection direction) {
        return switch (direction) {
            case UP -> PIPE_EXIT_TICKS_UP;
            case DOWN -> PIPE_EXIT_TICKS_DOWN;
            case LEFT, RIGHT -> PIPE_EXIT_TICKS_SIDE;
        };
    }

    public boolean isWalkingIntoSidePipe() {
        return isEnteringSidePipe()
            && pipeCountdown < PIPE_ENTRY_TICKS
            && pipeCountdown >= PIPE_SIDE_MOVE_MIN_COUNTDOWN;
    }

    public boolean isHiddenInPipe() {
        if (!isEnteringPipe() || pipeCountdown == PIPE_ENTRY_TICKS) {
            return false;
        }
        return pipeCountdown == 0 || (isEnteringSidePipe() && pipeCountdown < PIPE_SIDE_MOVE_MIN_COUNTDOWN);
    }

    public int getPipeWalkBeat() {
        final int ticksSinceStart = isExitingPipe()
            ? PIPE_EXIT_TICKS_SIDE - pipeCountdown
            : PIPE_ENTRY_TICKS - pipeCountdown;
        return (ticksSinceStart & 12) >> 2;
    }

    public boolean isCarrying() {
        return carriedObject != null;
    }

    public boolean isTurningAroundWhileCarrying() {
        return turnAroundFaceCounter > 0;
    }

    public boolean isCarriedObjectInFront() {
        return isTurningAroundWhileCarrying() || isInPipe();
    }

    /**
     * Advances the turn-around pose by one tick.
     *
     * <p>Called <em>after</em> the animator has run, for the same reason
     * {@link #decrementHurtInvincibility()} is: the counter is decremented inside the frame selection,
     * right after the test that chose the face-forward frame, so this tick's pose belongs to the
     * pre-decrement value.
     */
    public void decrementTurnAroundFace() {
        if (turnAroundFaceCounter > 0) {
            turnAroundFaceCounter--;
        }
    }

    public boolean isKicking() {
        return kickCountdown > 0;
    }

    public void decrementKick() {
        if (kickCountdown > 0) {
            kickCountdown--;
        }
    }

    /** @return whether the small→Super grow transition is in progress. */
    public boolean isChangingSize() {
        return growShrinkCounter > 0;
    }

    /** Advances the grow transition by one frame. */
    public void decrementGrow() {
        if (growShrinkCounter > 0) {
            growShrinkCounter--;
        }
    }

    /** @return whether the large→Raccoon poof transition is in progress. */
    public boolean isTurningToRaccoon() {
        return poofCounter > 0;
    }

    /** Advances the poof transition by one frame. */
    public void decrementPoof() {
        if (poofCounter > 0) {
            poofCounter--;
        }
    }

    /** @return whether post-hit flashing invincibility is still running. */
    public boolean isHurtInvincible() {
        return hurtInvincibilityCounter > 0;
    }

    /**
     * Whether the player sprite is drawn on this tick, given the flashing invincibility phase.
     *
     * <p>The read order matters: the counter is read, then decremented, and the phase mask is applied to
     * the value read before the decrement, so the phase belongs to the pre-decrement value.
     * {@link #decrementHurtInvincibility} is therefore called after this, not before.
     *
     * <p>A player who fell into a pit is never drawn: that death only waits out its countdown.
     *
     * @return {@code true} to draw the player, {@code false} to skip it this tick
     */
    public boolean isSpriteDrawnThisTick() {
        if (isDyingByFallingOff() || isHiddenInPipe()) {
            return false;
        }
        return !isHurtInvincible() || (hurtInvincibilityCounter & INVINCIBILITY_BLANK_PHASE_MASK) == 0;
    }

    /** Advances the flashing invincibility by one frame. */
    public void decrementHurtInvincibility() {
        if (hurtInvincibilityCounter > 0) {
            hurtInvincibilityCounter--;
        }
    }

    @Override
    public int nextKillTally() {
        final int award = killTally;
        killTally = KillTallyAware.super.nextKillTally();
        return award;
    }

    /**
     * Engages ducking.
     * Called when the player is grounded and DOWN is held.
     */
    public void duck() {
        ducking = true;
    }

    /**
     * Disengages ducking.
     * Called when the player lands with DOWN released, or when forced
     * by size change / holding / sliding conditions.
     */
    public void standUp() {
        ducking = false;
    }

    public void stop() {
        movement = STILL;
        killTally = 0;
    }

    /**
     * Transitions to airborne (walked off a ledge or otherwise became airborne
     * without jumping).
     */
    public void fall() {
        movement = FALLING;
    }

    public void setTo(final PlayerMovement playerMovement) {
        movement = playerMovement;
    }
}
