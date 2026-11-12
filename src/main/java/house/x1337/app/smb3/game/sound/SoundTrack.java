package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.GameContext;

@Prototype
public interface SoundTrack {
    String getLabel();
    GameContext getContext();

    default boolean isLooping() {
        return false;
    }
}
