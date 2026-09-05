package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import lombok.Getter;
import lombok.Setter;

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
@Getter
@Prototype
public class PlayerRuntimeState {
    /**
     * Number of ticks the small→Super grow transition runs (dasm
     * {@code ObjHit_PUpMush} @ PRG001_A8AB: {@code LDA #$2f; STA Player_Grow}).
     */
    public static final int GROW_TRANSITION_TICKS = 47;

    private PlayerMovement movement = STILL;

    /**
     * Separate ducking flag mirroring the original {@code Player_IsDucking}.
     * This flag persists when the player becomes airborne (duck-jump) and is
     * only cleared on landing when DOWN is released, or forcefully by certain
     * conditions (holding objects, sliding, etc.). The animator uses this to
     * keep the duck frame rendered even while the movement mode is
     * JUMPING/FALLING/FLYING.
     */
    private boolean ducking;

    /**
     * Frames of grace remaining after leaving a low-clearance (emexit) region,
     * during which horizontal wall correction is suppressed to avoid a camera
     * jolt. Set to 4 on entering low clearance and decremented each frame.
     */
    @Setter
    private int lowClearanceGrace;

    /**
     * Raccoon tail-attack countdown (dasm prg008 {@code Player_TailAttackAnim},
     * initialised to {@code $12}). Auto-decrements each frame; zero means no
     * attack in progress.
     */
    @Setter
    private int playerTailAttackCountdown;

    /**
     * Raccoon tail-wag countdown (dasm {@code Player_WagCount}) controlling the
     * slow-fall / flight Y-velocity cap while airborne.
     */
    @Setter
    private int playerWagCount;

    /**
     * Remaining flight frames (dasm {@code Player_FlyTime}) granted on a full
     * P-meter jump launch.
     */
    @Setter
    private int playerFlyTime;

    /**
     * Alternating toggle used to decrement {@link #playerFlyTime} every other
     * frame (halves the flight-timer tick rate).
     */
    @Setter
    private int flyTimeToggle;

    /**
     * Player run flag (dasm {@code Player_RunFlag}): set when grounded, holding
     * B, and moving at or above the run threshold.
     */
    @Setter
    private boolean running;

    /**
     * Remaining ticks of the small→Super grow transition (dasm
     * {@code Player_Grow}, initialised to {@code $2f} by {@code ObjHit_PUpMush}
     * @ PRG001_A8AB). While non-zero the player is "growing": it halts gameplay
     * (dasm {@code Player_HaltGame = ... ORA Player_Grow}, prg008 PRG008_A1B4)
     * and its draw routine plays the grow flicker (prg029 PRG029_D224). The
     * counter is decremented once per frame and the size flip to NORMAL happens
     * when it reaches zero.
     */
    @Setter
    private int growCounter;

    public boolean isInAir() {
        return movement == JUMPING || movement == FALLING || movement == FLYING;
    }

    public boolean isTransitioning() {
        return isGrowing();
    }

    /** @return whether the small→Super grow transition is in progress. */
    public boolean isGrowing() {
        return growCounter > 0;
    }

    /** Advances the grow transition by one frame (dasm {@code DEC Player_Grow}). */
    public void decrementGrow() {
        if (growCounter > 0) {
            growCounter--;
        }
    }

    /**
     * Returns whether the player is ducking. This checks the independent
     * ducking flag rather than the movement mode, allowing ducking to
     * coexist with airborne states (duck-jump).
     */
    public boolean isDucking() {
        return ducking;
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

    /**
     * Transitions to a grounded state. Defaults to {@link PlayerMovement#STILL}
     * as a landing state; the tick logic refines it afterward.
     */
    public void stop() {
        movement = STILL;
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
