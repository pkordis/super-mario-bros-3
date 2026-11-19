package house.x1337.app.smb3.game.object.level.enemy.base;

import house.x1337.app.smb3.game.object.level.SlopeFollowingCapable;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.SlopeProfile;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;

/**
 * The world-collision probes and shared constants every enemy that walks on the terrain needs. The ground probes,
 * slopes included, come from {@link SlopeFollowingCapable}, which the walking power-ups share.
 *
 * <p>Deliberately a mixin rather than something pushed up to {@code ActiveLevelObject}: world collision
 * is composed into the entities that need it, a decision already made and paid for (a {@code SuperLeaf} ignores
 * tiles entirely, a {@code SuperMushroom} does not). This is the same idea one level down - shared by the ground
 * walkers, imposed on nothing else.
 */
public interface GroundWalkingEnemy extends EnemyLevelObject, SlopeFollowingCapable {
    int BOUNDING_BOX_HEIGHT = 8;
    int BOUNDING_BOX_LEFT_OFFSET = 1;
    int BOUNDING_BOX_TOP_OFFSET = 2;
    int BOUNDING_BOX_WIDTH = 13;
    int GRAVITY_FIXED_POINT = 3;
    int MAX_FALL_FIXED_POINT = 64;
    int NO_HIT_COOLDOWN_TICKS = 0;
    int STOMP_HIT_COOLDOWN_TICKS = 8;
    int TICKS_PER_WALK_FRAME = 8;
    int WALK_SPEED_FIXED_POINT = 8;

    /**
     * Maps a sprite-pixel position to the quad's world translation, which jME takes as the quad's
     * <b>bottom-left</b> corner.
     *
     * <p>That anchoring is why this is height-agnostic: the bottom edge is the enemy's feet line at
     * {@code Y + BODY_SIZE_PIXELS} whatever the quad's height, so a taller sprite simply grows upward
     * from the same point.
     *
     * @param position the enemy's current sprite-pixel position
     * @return the world offset to translate the sprite to, at the enemy's own base depth, or {@code null}
     *         if it has no geometry yet
     */
    default WorldOffset pixelToWorldOffset(final PixelPosition position) {
        if (getSpriteGeometry() == null) {
            return null;
        }
        final int rows = getGameEngine().getLevelScene().getDimensions().rows();
        final float worldX = (float) (position.x() / TILE_SPRITE_SIZE);
        // Drop the sprite by one logical pixel, for the same reason SuperMushroom does: resting feet
        // are mathematically flush with the tile top but rasterise a pixel high, leaving a seam above
        // the surface. Purely visual - the collision maths above is untouched.
        final double drawY = position.y() + slopeDrawOffsetPixels(position);
        final float worldY = (rows - 1) - (float) (drawY / TILE_SPRITE_SIZE) - 1f / TILE_SPRITE_SIZE;
        return WorldOffset.of(
            worldX,
            worldY,
            getBaseDepth()
        );
    }

    default int walkSpeedFixedPoint(final boolean facingRight) {
        final int centreX = (int) floor(getPosition().x() + BODY_SIZE_PIXELS / 2.0);
        final int feetY = (int) floor(getPosition().y()) + BODY_SIZE_PIXELS;
        return getCollisionGrid()
            .findSlopeAt(of(floorDiv(centreX, TILE_SPRITE_SIZE), floorDiv(feetY, TILE_SPRITE_SIZE)))
            .filter(slope -> slope.isUphillFor(facingRight))
            .map(SlopeProfile::enemyUphillWalkSpeed)
            .orElse(WALK_SPEED_FIXED_POINT);
    }

    /**
     * @param proposed the position the enemy is about to move to
     * @return {@code true} if a solid tile sits over its head there
     */
    default boolean isCeilingAbove(final PixelPosition proposed) {
        final int centerColumn = getPosition().plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int headRow = proposed.row();
        return getCollisionGrid().isWallOrCeilingSolidTile(of(centerColumn, headRow));
    }
}
