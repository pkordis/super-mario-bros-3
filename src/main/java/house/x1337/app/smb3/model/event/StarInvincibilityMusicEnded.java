package house.x1337.app.smb3.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published on the tick the star invincibility has little enough left that the level's music takes over again. The
 * invincibility itself runs on for a moment. A death or the end-level card publishes it too, to end the star music.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StarInvincibilityMusicEnded extends GameEvent {
}
