package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.GameObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;

public interface LevelObject extends GameObject {
    Offset getOffset();
    LevelObjectType getType();
    void onCollisionFromBelow(LevelScenePlayer levelScenePlayer);

    default boolean isCollidable() {
        return true;
    }

    default boolean isOneWayPlatform() {
        return false;
    }
}
