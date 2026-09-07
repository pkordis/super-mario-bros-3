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

    /**
     * Reacts to being struck by the raccoon/tanooki tail attack (dasm prg000
     * {@code Object_RespondToTailAttack}). Invoked once per tick for each object whose bounds
     * the player's tail hitbox intersects, on the two "kick" frames of the swing.
     *
     * <p>Intentionally has <b>no</b> default implementation: every {@link LevelObject} must state
     * its own response (even if that is to do nothing). This is the project's stand-in for the
     * ROM's per-object {@code OA3_TAILATKIMMUNE} attribute — immunity is expressed by an empty body.
     *
     * @param levelScenePlayer the player whose tail struck this object
     */
    void onTailAttack(LevelScenePlayer levelScenePlayer);

    default void onCollisionFromBelow(LevelScenePlayer levelScenePlayer) {
        // Do nothing by default
    }

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
