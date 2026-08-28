package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerOrientationVertical;

public interface LevelScenePlayerOrientationAware {
    PlayerOrientationHorizontal getOrientationHorizontal();
    PlayerOrientationVertical getOrientationVertical();
    void setOrientationHorizontal(PlayerOrientationHorizontal orientation);
    void setOrientationVertical(PlayerOrientationVertical orientation);
}
