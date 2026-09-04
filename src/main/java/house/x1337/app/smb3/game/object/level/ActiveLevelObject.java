package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.model.game.WorldOffset.of;

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
public interface ActiveLevelObject extends LevelObject, GameEngineAware {
    double getPixelX();
    double getPixelY();
    ImageResource getImageResource();
    Dimensions getSpriteDimensions();

    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(getPixelX(), getPixelY(), getImageResource().getDimensions());
    }
    Geometry getSpriteGeometry();
    void onCollisionWith(LevelScenePlayer player);

    default boolean intersects(final AxisAlignedBoundingBox playerBounds) {
        return getBounds().intersects(playerBounds);
    }

    default void detach() {
        getGameEngine()
            .getRootNode()
            .detachChild(getSpriteGeometry());
    }

    default WorldOffset getCurrentWorldOffset() {
        final int rows = getGameEngine().getLevelScene().getDimensions().rows();
        final float worldX = (float) (getPixelX() / TILE_SPRITE_SIZE);
        final float topEdgeWorldY = (rows - 1) - (float) (getPixelY() / TILE_SPRITE_SIZE) + getSpriteDimensions().height();
        return of(worldX, topEdgeWorldY, Z_DEPTH_ITEM_REWARD);
    }
}
