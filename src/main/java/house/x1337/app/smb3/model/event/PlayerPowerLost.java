package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published when a full P-meter stops being full: it drains a step, the flight it paid for ends, or a fresh start
 * empties it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerPowerLost extends GameEvent {
}
