package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Published when a pressed P-switch opens its window, turning bricks into coins and coins into bricks. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PowerSwitchActivated extends GameEvent {
}
