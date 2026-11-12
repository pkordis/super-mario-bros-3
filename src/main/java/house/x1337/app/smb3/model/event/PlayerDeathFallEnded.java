package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published once, when the body of a player killed by an enemy has fallen past the bottom of the level. The level
 * ends once the death music has ended too.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerDeathFallEnded extends GameEvent {
}
