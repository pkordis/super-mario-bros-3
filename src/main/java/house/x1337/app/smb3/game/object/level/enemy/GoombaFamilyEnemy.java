package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;

public interface GoombaFamilyEnemy extends EnemyLevelObject {
    int BIG_HOP_COOLDOWN_TICKS = 80;
    int BIG_HOP_Y_VELOCITY_FIXED_POINT = -48;
    int BODY_RIGHT_SPRITE_OFFSET_PIXELS = TILE_SPRITE_SIZE / 2;
    int BOUND_BOX_HEIGHT = 8;
    int BOUND_BOX_LEFT_OFFSET = 1;
    int BOUND_BOX_TOP_OFFSET = 2;
    int BOUND_BOX_WIDTH = 13;
    int CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT = 1;
    int GRAVITY_FIXED_POINT = 3;
    int HOPS_PER_CYCLE = 4;
    int LITTLE_HOP_Y_VELOCITY_FIXED_POINT = -16;
    int MAX_FALL_FIXED_POINT = 64;
    int NO_HIT_COOLDOWN_TICKS = 0;
    int OBJECT_CLOCK_WRAP = 255;
    int REFACE_PERIOD_MASK = 63;
    int RISING_WING_FLAP_BONUS = 2;
    int SPRITE_SIZE_PIXELS = TILE_SPRITE_SIZE;
    int STOMP_HIT_COOLDOWN_TICKS = 8;
    int TICKS_PER_WALK_FRAME = 8;
    int WALK_SPEED_FIXED_POINT = 8;
    int WING_FLAP_PHASE_BIT = 8;
    int WING_LIFT_FIXED_POINT = 1;
    int WING_LIFT_PIXELS = 10;
    int WING_OUTWARD_OFFSET_PIXELS = 2;

    default WorldOffset pixelToWorldOffset(final PixelPosition position) {
        final Geometry spriteGeometry = getSpriteGeometry();
        if (spriteGeometry == null) {
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
     * @return {@code true} if a solid tile sits under its feet there
     */
    default boolean isGroundBelow(final PixelPosition proposed) {
        final int centerColumn = getPosition().plusX(SPRITE_SIZE_PIXELS / 2.0).column();
        final int feetRow = proposed.plusY(SPRITE_SIZE_PIXELS).row();
        return getCollisionGrid().isGroundSolidTile(centerColumn, feetRow);
    }

    /**
     * @param proposed the position the enemy is about to move to
     * @return {@code true} if its leading edge (right when travelling right, left otherwise) would
     *         enter a solid tile there
     */
    default boolean isWallAhead(final PixelPosition proposed) {
        final int midRow = getPosition().plusY(SPRITE_SIZE_PIXELS / 2.0).row();
        final int leadingColumn = getVelocity().movingRight()
            ? proposed.plusX(SPRITE_SIZE_PIXELS - 1).column()
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
