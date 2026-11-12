package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Published once, on the countdown step that brings the level timer down to its hurry-up mark. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerTimerHurryUp extends GameEvent {
}
