package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published on the tick gameplay stops: a player halts it (dying, in a pipe, changing suit) or a blocking fade
 * does.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GameplayHalted extends GameEvent {
}
