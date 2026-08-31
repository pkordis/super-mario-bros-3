package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

/**
 * A dynamic, self-moving level entity (rewards, and — in future — enemies and projectiles) that
 * advances every simulation tick and can collide with the scene's players.
 *
 * <p>Collision is expressed purely as box overlap in sprite-pixel space: an object exposes its
 * collision box via {@link #getBounds()}, a player exposes its hitbox via
 * {@code LevelScenePlayer.getObjectCollisionBounds()} (computed once per tick, not per object),
 * and the two are tested with {@link #intersects(AxisAlignedBoundingBox)}. A broadphase ({@code ActiveObjectGrid})
 * narrows candidate pairs before that test so the cost stays bounded as the number of active
 * objects grows.
 *
 * <p>This is intentionally separate from the static terrain {@code CollisionGrid}: that resolves
 * the player against fixed, tile-aligned solids, whereas this concerns moving-object overlap.
 */
public interface ActiveLevelObject extends GameEngineAware, LevelObject {
    /**
     * @return this object's collision box in sprite-pixel space, for the current tick
     */
    AxisAlignedBoundingBox getBounds();

    /**
     * Reacts to being collided with by a player (e.g. a reward grants points; an enemy stomps or
     * hurts). Invoked by the collision driver after a broadphase-narrowed overlap test succeeds.
     *
     * @param player the player that collided with this object
     */
    void onCollisionWith(LevelScenePlayer player);

    Geometry getSpriteGeometry();

    /**
     * @param playerBounds a player's hitbox, precomputed once per tick
     * @return {@code true} if that hitbox overlaps this object's box this tick
     */
    default boolean intersects(final AxisAlignedBoundingBox playerBounds) {
        return getBounds().intersects(playerBounds);
    }

    default void detach() {
        getGameEngine()
            .getRootNode()
            .detachChild(getSpriteGeometry());
    }
}
