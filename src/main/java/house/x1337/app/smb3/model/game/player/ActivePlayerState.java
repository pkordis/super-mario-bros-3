package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerState;
import lombok.Getter;

import static house.x1337.app.smb3.enumeration.PlayerState.DUCKING;
import static house.x1337.app.smb3.enumeration.PlayerState.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerState.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerState.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerState.STILL;

/**
 * Holds the active movement state of a player.
 *
 * <p>The player is always in exactly one {@link PlayerState}. Query methods
 * like {@link #isInAir()} and {@link #isDucking()} derive their result from
 * the current state rather than maintaining separate boolean flags.
 */
@Getter
@Prototype
public class ActivePlayerState {
    private PlayerState current = STILL;

    public boolean isInAir() {
        return current == JUMPING || current == FALLING || current == FLYING;
    }

    public boolean isDucking() {
        return current == DUCKING;
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
