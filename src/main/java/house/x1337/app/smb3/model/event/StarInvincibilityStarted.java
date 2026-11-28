package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Published when the player collects a star, including one collected while the invincibility is still running. */
@Data
@EqualsAndHashCode(callSuper = true)
public class StarInvincibilityStarted extends GameEvent {
}
