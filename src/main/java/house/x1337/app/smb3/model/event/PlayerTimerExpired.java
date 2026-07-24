package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published when the level timer reaches zero by natural countdown.
 *
 * <p>From prg026.asm: when the status bar timer decrements to zero the player
 * loses a life. Subscribers should trigger the death/time-out sequence.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerTimerExpired extends GameEvent {
}
