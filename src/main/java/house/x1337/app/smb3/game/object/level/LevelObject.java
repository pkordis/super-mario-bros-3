package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.GameObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import org.slf4j.Logger;

import java.util.Map;

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

    default void configure(final Map<String, Object> data) {
        if (data != null && !data.isEmpty()) {
            final Logger log = getLogger(getClass());
            data.forEach((k, v) -> {
                log.warn("LevelObject-specific data attribute: {}={}, disregarded", k, v);
            });
        }
    }
}
