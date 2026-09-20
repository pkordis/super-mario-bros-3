package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.util.GameRenderer;

public interface Animation extends GameRenderer {
    void detach();
    WorldOffset getWorldOffset();
    boolean isExpired();
    void tick();
}
