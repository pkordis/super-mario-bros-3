package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
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
 * and the two are tested with {@link #intersects(AxisAlignedBoundingBox)}. A broad-phase ({@code ActiveObjectGrid})
 * narrows candidate pairs before that test so the cost stays bounded as the number of active
 * objects grows.
 *
 * <p>This is intentionally separate from the static terrain {@code CollisionGrid}: that resolves
 * the player against fixed, tile-aligned solids, whereas this concerns moving-object overlap.
 */
public interface ActiveLevelObject extends LevelObject, GameEngineAware {
    double FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS = 512;

    PixelPosition getPosition();
    FixedPointVelocity getVelocity();
    Dimensions getSpriteDimensions();
    AxisAlignedBoundingBox getBounds();
    Geometry getSpriteGeometry();
    void onCollisionWith(LevelScenePlayer player);

    default boolean resolvesDirectionalPlayerCollision() {
        return false;
    }

    default double getStompReferenceTop() {
        return getBounds().top();
    }

    default boolean bouncesOffOtherObjects() {
        return false;
    }

    default void onSideCollisionWith(final ActiveLevelObject other) {
        // Do nothing
    }

    default boolean isKickedHazard() {
        return false;
    }

    default void onStruckByKickedObject(final int knockXVelocityFixedPoint) {
        // Do nothing: not everything a shell can roll through is killable by it.
    }

    default void onKickedInto(final ActiveLevelObject other) {
        // Do nothing
    }

    default boolean isKilledByRammedObject() {
        return true;
    }

    default boolean isCarriedHazard() {
        return false;
    }

    default void onCarriedInto(final ActiveLevelObject other) {
        // Do nothing
    }

    default void expire() {
        // Do nothing: an object with no expiry of its own has nothing to retire.
    }

    default boolean hasLeftTheLevelSideways() {
        final double left = getPosition().x();
        final double right = left + getSpriteDimensions().width() / PIXELS_TO_GAME_UNITS;
        final double levelWidthPixels = (double) getLevelScene().getDimensions().columns() * TILE_SPRITE_SIZE;
        return right <= 0 || left >= levelWidthPixels;
    }

    default boolean hasFallenBelowTheLevel() {
        final double levelHeightPixels = (double) getLevelScene().getDimensions().rows() * TILE_SPRITE_SIZE;
        return getPosition().y() >= Math.max(FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS, levelHeightPixels);
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
        final PixelPosition position = getPosition();
        final int rows = getGameEngine().getLevelScene().getDimensions().rows();
        final float worldX = (float) (position.x() / TILE_SPRITE_SIZE);
        final float topEdgeWorldY = (rows - 1)
            - (float) (position.y() / TILE_SPRITE_SIZE)
            + getSpriteDimensions().height();
        return of(worldX, topEdgeWorldY, Z_DEPTH_ITEM_REWARD);
    }
}
