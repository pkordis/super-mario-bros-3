package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerState;
import lombok.Getter;

import static house.x1337.app.smb3.enumeration.PlayerState.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerState.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerState.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerState.STILL;

/**
 * Holds the active movement state of a player.
 *
 * <p>The player is always in exactly one {@link PlayerState} for movement
 * (STILL, WALKING, RUNNING, JUMPING, FALLING, FLYING, etc.). Ducking is
 * tracked as a separate flag ({@link #ducking}) that persists independently
 * of the movement state — matching the original NES implementation where
 * {@code Player_IsDucking} is a standalone variable that freezes once
 * airborne (dasm prg008 PRG008_A715: if already ducking when in air, the
 * flag is preserved; it is only re-evaluated on the ground).
 */
@Getter
@Prototype
public class ActivePlayerState {
    private PlayerState current = STILL;

    /**
     * Separate ducking flag mirroring the original {@code Player_IsDucking}.
     * This flag persists when the player becomes airborne (duck-jump) and is
     * only cleared on landing when DOWN is released, or forcefully by certain
     * conditions (holding objects, sliding, etc.). The animator uses this to
     * keep the duck frame rendered even while the movement state is
     * JUMPING/FALLING/FLYING.
     */
    private boolean ducking;

    public boolean isInAir() {
        return current == JUMPING || current == FALLING || current == FLYING;
    }

    /**
     * Returns whether the player is ducking. This checks the independent
     * ducking flag rather than the movement state, allowing ducking to
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
     * Transitions to a grounded state. Defaults to {@link PlayerState#STILL}
     * as a landing state; the tick logic refines it afterward.
     */
    public void stop() {
        current = STILL;
    }

    /**
     * Transitions to airborne (walked off a ledge or otherwise became airborne
     * without jumping).
     */
    public void fall() {
        current = FALLING;
    }

    public void setTo(final PlayerState playerState) {
        current = playerState;
    }
}
