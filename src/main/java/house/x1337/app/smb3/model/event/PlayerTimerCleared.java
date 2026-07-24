package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published when the level timer is explicitly cleared (set to zero) via
 * {@code PlayerTimer.clear()}, as opposed to expiring naturally.
 *
 * <p>This can occur during level transitions, cutscenes, or other non-death
 * scenarios where the timer is forcibly reset.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerTimerCleared extends GameEvent {
}
