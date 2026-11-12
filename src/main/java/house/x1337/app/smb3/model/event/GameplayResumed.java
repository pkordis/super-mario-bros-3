package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Published on the first tick gameplay runs again after a halt. */
@Data
@EqualsAndHashCode(callSuper = true)
public class GameplayResumed extends GameEvent {
}
