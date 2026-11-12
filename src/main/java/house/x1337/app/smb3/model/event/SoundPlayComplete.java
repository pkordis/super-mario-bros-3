package house.x1337.app.smb3.model.event;

import house.x1337.app.smb3.enumeration.MusicTrack;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published once when a track that plays once (a jingle) has played to its end. A track stopped or replaced before
 * its end publishes nothing, and a looping track never ends.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SoundPlayComplete extends GameEvent {
    private final MusicTrack track;
}
