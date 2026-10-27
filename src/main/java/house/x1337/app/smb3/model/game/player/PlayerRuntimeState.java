package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.KillTallyAware;
import lombok.Data;

import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;

/**
 * Holds the live, mutable runtime state of a player.
 *
 * <p>The player is always in exactly one {@link PlayerMovement} mode
 * (STILL, WALKING, RUNNING, JUMPING, FALLING, FLYING, etc.). Ducking is
 * tracked as a separate flag ({@link #ducking}) that persists independently
 * of the movement mode — matching the original NES implementation where
 * {@code Player_IsDucking} is a standalone variable that freezes once
 * airborne (dasm prg008 PRG008_A715: if already ducking when in air, the
 * flag is preserved; it is only re-evaluated on the ground).
 */
@Data
@Prototype
public class PlayerRuntimeState implements KillTallyAware {
    public static final int HURT_INVINCIBILITY_TICKS = 113;
    public static final int KICK_POSE_TICKS = 13;
    public static final int NORMAL_TRANSITION_TICKS = 47;
    public static final int RACCOON_TRANSITION_TICKS = 23;
    public static final int SUIT_LOSS_TRANSITION_TICKS = 23;
    public static final int TURN_AROUND_FACE_TICKS = 7;

    private static final int INVINCIBILITY_BLANK_PHASE_MASK = 2;
    private PlayerMovement movement = STILL;

    private CarriableLevelObject carriedObject;
    private PlayerMode queuedMode;
    private boolean ducking;
    private boolean dying;
    private boolean running;
    private boolean wasCarrying;
    private int deathPauseCounter;
    private int flyTimeToggle;
    private int growShrinkCounter;
    private int hurtInvincibilityCounter;
    private int kickCountdown;
    private int killTally;
    private int lowClearanceGrace;
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
        dying = true;
    }

    public boolean isDeathPausing() {
        return dying && deathPauseCounter > 0;
    }

    public void decrementDeathPause() {
        if (deathPauseCounter > 0) {
            deathPauseCounter--;
        }
    }

    public boolean isCarrying() {
        return carriedObject != null;
    }

    public boolean isTurningAroundWhileCarrying() {
        return turnAroundFaceCounter > 0;
    }

    /**
     * Advances the turn-around pose by one tick (dasm {@code DEC Player_PipeFace} @ PRG008_AF45).
     *
     * <p>Called <em>after</em> the animator has run, for the same reason
     * {@link #decrementHurtInvincibility()} is: the ROM decrements it inside the frame-selection code,
     * one instruction after the test that chose the face-forward frame, so this tick's pose belongs to
     * the pre-decrement value.
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

    /** Advances the grow transition by one frame (dasm {@code DEC Player_Grow}). */
    public void decrementGrow() {
        if (growShrinkCounter > 0) {
            growShrinkCounter--;
        }
    }

    /** @return whether the large→Raccoon poof transition is in progress. */
    public boolean isTurningToRaccoon() {
        return poofCounter > 0;
    }

    /** Advances the poof transition by one frame (dasm {@code DEC Player_SuitLost}). */
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
     * <p>Mirrors {@code Player_Draw} @ PRG029_CEC8 exactly, including the read order: the ROM loads the
     * counter, decrements <em>memory</em>, then masks the value it had already loaded — so the phase
     * belongs to the pre-decrement value. {@link #decrementHurtInvincibility} is therefore called after
     * this, not before.
     *
     * @return {@code true} to draw the player, {@code false} to skip it this tick
     */
    public boolean isSpriteDrawnThisTick() {
        return !isHurtInvincible() || (hurtInvincibilityCounter & INVINCIBILITY_BLANK_PHASE_MASK) == 0;
    }

    /** Advances the flashing invincibility by one frame (dasm {@code DEC Player_FlashInv}). */
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
     * Engages ducking (dasm: {@code Player_IsDucking = suit value}).
     * Called when the player is grounded and DOWN is held.
     */
    public void duck() {
        ducking = true;
    }

    /**
     * Disengages ducking (dasm: {@code Player_IsDucking = 0}).
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
