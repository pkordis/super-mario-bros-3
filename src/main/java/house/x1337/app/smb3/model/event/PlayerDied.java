package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Raised once the player's death throe has fully played out — the body has risen and fallen off the
 * bottom of the level (dasm {@code Player_Die} drops the player back to the map after the throe). The
 * engine intercepts this to restart the level scene from scratch.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerDied extends GameEvent {
}
