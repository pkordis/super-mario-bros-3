package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Published on the tick the player's P-meter becomes full. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerPowerReached extends GameEvent {
}
