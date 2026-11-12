package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published on the tick the P-switch window runs out. A window closed early, by a level build or by the end-level
 * card, publishes nothing.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PowerSwitchExpired extends GameEvent {
}
