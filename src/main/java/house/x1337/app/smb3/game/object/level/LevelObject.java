package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.GameObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

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

    default void configure(final LevelObjectData data) {
        if (data.areAvailable()) {
            final Logger log = getLogger(getClass());
            data.asMap().forEach((k, v) -> log.warn("LevelObject-specific data attribute: {}={}, disregarded", k, v));
        }
    }
}
