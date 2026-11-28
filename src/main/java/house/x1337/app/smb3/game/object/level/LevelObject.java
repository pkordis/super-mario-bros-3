package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.GameObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.SlopeProfile;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public interface LevelObject extends GameObject {
    Coordinates getOffset();
    LevelObjectType getType();

    default void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        // Do nothing by default
    }

    /**
     * A kicked object ran into this tile from the side. It strikes like a tail attack unless a tile tells the two
     * apart; {@code levelScenePlayer} is the player closest to the strike.
     */
    default void onKickedObjectStrike(final LevelScenePlayer levelScenePlayer) {
        onTailAttack(levelScenePlayer);
    }

    default void onCollisionFromBelow(final LevelScenePlayer leveelScenePlayer) {
        // Do nothing by default
    }

    default void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        // Do nothing by default
    }

    default void onPlayerOverlap(final LevelScenePlayer levelScenePlayer) {
        // Do nothing by default
    }

    default boolean isCollidable() {
        return true;
    }

    /**
     * Whether the object is not drawn and not solid until the player's head or tail strikes it. See
     * kb/invisible-block.md.
     */
    default boolean isHidden() {
        return false;
    }

    default boolean isPassableForPlayers() {
        return false;
    }

    default boolean isOneWayPlatform() {
        return false;
    }

    default Optional<SlopeProfile> findSlopeProfile() {
        return Optional.empty();
    }

    default void configure(final LevelObjectData data) {
        if (data.areAvailable()) {
            final Logger log = getLogger(getClass());
            data.asMap().forEach((k, v) -> log.warn("LevelObject-specific data attribute: {}={}, disregarded", k, v));
        }
    }
}
