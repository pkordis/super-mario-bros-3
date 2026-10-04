package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
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
    Dimensions getSpriteDimensions();
    AxisAlignedBoundingBox getBounds();
    Geometry getSpriteGeometry();
    void onCollisionWith(LevelScenePlayer player);

    default boolean resolvesDirectionalPlayerCollision() {
        return false;
    }

    /**
     * The Y the stomp-depth check measures the player's top against - the ROM's {@code Objects_Y} in
     * {@code Player_HitEnemy} (dasm prg000 @ PRG000_D218), described there as "generally the top of the
     * enemy". It is deliberately <b>not</b> the {@link #getBounds() collision box} top: the ROM selects
     * the stomp reference and the overlap box from two separate tables, so an enemy whose box is inset
     * from its sprite (a Goomba's starts {@code +2} down) is still judged stompable from its sprite top.
     * Defaults to the box top for objects that do not distinguish the two.
     *
     * @return the Y the {@code STOMP_RANGE} offset is applied to
     */
    default double getStompReferenceTop() {
        return getBounds().top();
    }

    default boolean bouncesOffOtherObjects() {
        return false;
    }

    default void onSideCollisionWith(final ActiveLevelObject other) {
        // Do nothing
    }

    default boolean intersects(final AxisAlignedBoundingBox playerBounds) {
        return getBounds().intersects(playerBounds);
    }

    default void detach() {
        final Geometry spriteGeometry = getSpriteGeometry();
        if (spriteGeometry != null) {
            getGameEngine()
                .getRootNode()
                .detachChild(spriteGeometry);
        }
    }

    default WorldOffset getCurrentWorldOffset() {
        final int rows = getGameEngine().getLevelScene().getDimensions().rows();
        final float worldX = (float) (getPixelX() / TILE_SPRITE_SIZE);
        final float topEdgeWorldY = (rows - 1) - (float) (getPixelY() / TILE_SPRITE_SIZE) + getSpriteDimensions().height();
        return of(worldX, topEdgeWorldY, Z_DEPTH_ITEM_REWARD);
    }
}
