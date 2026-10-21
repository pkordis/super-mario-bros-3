package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;

/**
 * The world-collision probes and shared constants every enemy that walks on the terrain needs.
 *
 * <p>Deliberately a mixin rather than something pushed up to {@code ActiveLevelObject}: world collision
 * is composed into the entities that need it, which {@code kb/dynamic-entities.md} records as a decision
 * already made and paid for (a {@code SuperLeaf} ignores tiles entirely, a {@code SuperMushroom} does
 * not). This is the same idea one level down - shared by the ground walkers, imposed on nothing else.
 */
public interface GroundWalkingEnemy extends EnemyLevelObject {
    int BODY_SIZE_PIXELS = TILE_SPRITE_SIZE;
    int BOUND_BOX_HEIGHT = 8;
    int BOUND_BOX_LEFT_OFFSET = 1;
    int BOUND_BOX_TOP_OFFSET = 2;
    int BOUND_BOX_WIDTH = 13;
    int FLOOR_DETECTION_DEPTH_LIMIT_PIXELS = 4;
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
     * @return the world offset to translate the sprite to, or {@code null} if it has no geometry yet
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
        final float worldY = (rows - 1) - (float) (position.y() / TILE_SPRITE_SIZE) - 1f / TILE_SPRITE_SIZE;
        return WorldOffset.of(
            worldX,
            worldY,
            Z_DEPTH_ENEMY
        );
    }

    /**
     * @param proposed the position the enemy is about to move to
     * @return {@code true} if a solid tile sits under its feet there <em>and</em> it is shallow enough
     *         into that tile to be landing on it
     */
    default boolean isGroundBelow(final PixelPosition proposed) {
        if (!isWithinFloorDetectionDepth(proposed)) {
            return false;
        }
        final int centerColumn = getPosition().plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
        return getCollisionGrid().isGroundSolidTile(centerColumn, feetRow);
    }

    /**
     * {@code Object_WorldDetect4}'s depth limit - see
     * {@link #FLOOR_DETECTION_DEPTH_LIMIT_PIXELS}. Without it, {@code Object_HitGround}'s align-to-tile
     * becomes an upward teleport for anything that got inside a surface without descending onto it: a
     * tail-whammed shell drifting sideways over a one-way platform at the top of its arc is sunk ten
     * pixels into it, and would be snapped up onto a ledge it never cleared.
     *
     * @param proposed the position the enemy is about to move to
     * @return {@code true} while the enemy's tracked Y is in the top {@code 4} px of its tile row
     */
    private boolean isWithinFloorDetectionDepth(final PixelPosition proposed) {
        final double depthIntoTile = proposed.y() - (double) proposed.row() * TILE_SPRITE_SIZE;
        return depthIntoTile < FLOOR_DETECTION_DEPTH_LIMIT_PIXELS;
    }

    /**
     * @param proposed the position the enemy is about to move to
     * @return {@code true} if a solid tile sits over its head there
     */
    default boolean isCeilingAbove(final PixelPosition proposed) {
        final int centerColumn = getPosition().plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int headRow = proposed.row();
        return getCollisionGrid().isWallOrCeilingSolidTile(centerColumn, headRow);
    }

    /**
     * @param proposed the position the enemy is about to move to
     * @return {@code true} if its leading edge (right when travelling right, left otherwise) would
     *         enter a solid tile there
     */
    default boolean isWallAhead(final PixelPosition proposed) {
        final int midRow = getPosition().plusY(BODY_SIZE_PIXELS / 2.0).row();
        final int leadingColumn = getVelocity().movingRight()
            ? proposed.plusX(BODY_SIZE_PIXELS - 1).column()
            : proposed.column();
        return getCollisionGrid().isWallOrCeilingSolidTile(leadingColumn, midRow);
    }

    default boolean hasFallenOffLevel() {
        final int rows = getLevelScene().getDimensions().rows();
        // Compared as a real number of tiles rather than via PixelPosition.row(): flooring first
        // would keep an enemy alive for the final fractional tile of its fall.
        return getPosition().y() / TILE_SPRITE_SIZE > rows + 1;
    }
}
