package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.GameObject;

public interface LevelObject extends GameObject {
    LevelObjectType getType();

    default boolean isCollidable() {
        return true;
    }

    default boolean isOneWayPlatform() {
        return false;
    }
}
