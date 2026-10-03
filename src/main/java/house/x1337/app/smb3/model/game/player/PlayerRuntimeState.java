package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
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
    public static final int NORMAL_TRANSITION_TICKS = 47;

    /**
     * Number of ticks the large→Raccoon "poof" suit-change runs (dasm
     * {@code ObjHit_SuperLeaf} @ PRG001_AC40: {@code LDA #$17; STA Player_SuitLost}).
     */
    public static final int RACCOON_TRANSITION_TICKS = 23;

    /**
     * Number of ticks the advanced→Normal suit-loss "poof" runs.
     *
     * <p>The same {@code Player_SuitLost} counter and the same {@code $17}, written by the hurt path
     * rather than by a powerup (dasm {@code Player_GetHurt} @ PRG000_DA15: {@code LDA #$17; STA
     * Player_SuitLost}). Named separately from {@link #RACCOON_TRANSITION_TICKS} because the two are
     * only incidentally equal — they are written by different routines and either could be retuned.
     */
    public static final int SUIT_LOSS_TRANSITION_TICKS = 23;

    /**
     * Ticks of flashing invincibility granted by a damaging hit (dasm {@code Player_GetHurt} @
     * PRG000_DA6D: {@code LDA #$71; STA Player_FlashInv}).
     */
    public static final int HURT_INVINCIBILITY_TICKS = 0x71;

    /**
     * The bit of {@link #hurtInvincibilityCounter} that blanks the player sprite.
     *
     * <p>The ROM's draw routine tests it with {@code AND #$02} (prg029 {@code Player_Draw} @
     * PRG029_CEC8), so the sprite is drawn for two ticks and skipped for the next two — a 4-tick
     * flicker period, not an every-other-frame one.
     */
    private static final int INVINCIBILITY_BLANK_PHASE_MASK = 0x02;

    private PlayerMovement movement = STILL;

    /**
     * Separate ducking flag mirroring the original {@code Player_IsDucking}.
     * This flag persists when the player becomes airborne (duck-jump) and is
     * only cleared on landing when DOWN is released, or forcefully by certain
     * conditions (holding objects, sliding, etc.). The animator uses this to
     * keep the duck frame rendered even while the movement mode is
     * JUMPING/FALLING/FLYING.
     * -- GETTER --
     *  Returns whether the player is ducking. This checks the independent
     *  ducking flag rather than the movement mode, allowing ducking to
     *  coexist with airborne states (duck-jump).

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
     * @ PRG001_A8AB). While non-zero the player is "growing/shrinking": it halts gameplay
     * (dasm {@code Player_HaltGame = ... ORA Player_Grow}, prg008 PRG008_A1B4)
     * and its draw routine plays the grow/shrinking flicker (prg029 PRG029_D224). The
     * counter is decremented once per frame and the size flip to NORMAL/SHRUNK happens
     * when it reaches zero.
     */
    @Setter
    private int growShrinkCounter;

    /**
     * Remaining ticks of the large→Raccoon "poof" suit-change (dasm
     * {@code Player_SuitLost}, initialised to {@code $17} by {@code ObjHit_SuperLeaf}
     * @ PRG001_AC40). While non-zero the player is "poofing": it halts gameplay
     * (dasm {@code Player_HaltGame = ... ORA Player_SuitLost}, prg008 PRG008_A1B4)
     * and its draw routine plays the poof cloud (prg029 {@code Player_SuitLost_DoPoof})
     * in place of the player sprite. The counter is decremented once per frame and
     * the suit flip to RACCOON happens when it reaches zero.
     */
    @Setter
    private int poofCounter;

    /**
     * The mode the running transition will hand the player once its counter reaches zero (dasm
     * {@code Player_QueueSuit}, which every transition writes alongside its own counter —
     * {@code ObjHit_PUpMush}, {@code ObjHit_SuperLeaf} and {@code Player_GetHurt} all do).
     *
     * <p>Held here rather than inferred from the counter that is running, because the poof counter
     * alone no longer identifies a destination: the same {@code Player_SuitLost} poof plays for the
     * large→Raccoon promotion and for the advanced→Normal suit loss, in opposite directions.
     */
    @Setter
    private PlayerMode queuedMode;

    /**
     * Remaining ticks of post-hit flashing invincibility (dasm {@code Player_FlashInv}, initialised to
     * {@code $71} by {@code Player_GetHurt} @ PRG000_DA6D). While non-zero the player cannot be hurt
     * again ({@code Player_GetHurt} returns immediately) and its sprite flickers.
     *
     * <p>Unlike {@link #growShrinkCounter} and {@link #poofCounter} this does <b>not</b> halt gameplay — it is
     * consumed purely at draw time, and the ROM decrements it inside {@code Player_Draw} itself. That
     * placement is what staggers the two effects when a hit starts both: while {@code Player_SuitLost} is
     * non-zero the draw dispatcher plays the poof and returns without ever reaching {@code Player_Draw}
     * (prg029 @ PRG029_D205), so this counter is held at its full value for the whole poof and only
     * begins running once gameplay resumes.
     */
    @Setter
    private int hurtInvincibilityCounter;

    public boolean isInAir() {
        return movement == JUMPING || movement == FALLING || movement == FLYING;
    }

    public boolean isTransitioning() {
        return isChangingSize() || isTurningToRaccoon();
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
