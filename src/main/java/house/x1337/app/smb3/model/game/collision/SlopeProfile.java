package house.x1337.app.smb3.model.game.collision;

import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.abs;
import static java.lang.Math.floorMod;
import static lombok.AccessLevel.PRIVATE;

/**
 * The walkable surface of a ramp tile. Each shape gives the depth of its straight ramp line at the tile's left and
 * right edge, which drawing follows, and for each pixel column how many pixels below the tile's top the surface lies,
 * which the physics uses. See {@code kb/slopes.md}, "Ramp shape".
 *
 * <p>Pulls and walk speeds are in sixteenths of a pixel per frame. A positive pull points to the right.
 */
@RequiredArgsConstructor(access = PRIVATE)
public enum SlopeProfile {
    GENTLE_FALLING_HIGH(0, 8, new int[] {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 6, 7}),
    GENTLE_FALLING_LOW(8, 16, new int[] {8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 13, 14, 14, 15}),
    GENTLE_RISING_HIGH(8, 0, new int[] {7, 6, 6, 5, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0, 0}),
    GENTLE_RISING_LOW(16, 8, new int[] {15, 14, 14, 13, 13, 13, 12, 12, 11, 11, 10, 10, 9, 9, 8, 8}),
    STEEP_FALLING(0, 16, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}),
    STEEP_RISING(16, 0, new int[] {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0});

    static final int GENTLE_ENEMY_UPHILL_WALK_SPEED = 6;
    static final int GENTLE_PLAYER_PULL = 2;
    static final int STEEP_ENEMY_UPHILL_WALK_SPEED = 5;
    static final int STEEP_PLAYER_PULL = 3;

    private final int leftEdgeDepth;
    private final int rightEdgeDepth;
    private final int[] surfaceDepths;

    public int surfaceDepthAt(final int column) {
        return surfaceDepths[floorMod(column, TILE_SPRITE_SIZE)];
    }

    /**
     * @param column the pixel column, in level pixels or within the tile
     * @param row    the pixel row, in level pixels or within the tile
     * @return whether that pixel is at or below the surface
     */
    public boolean isSolidAt(
        final int column,
        final int row
    ) {
        return floorMod(row, TILE_SPRITE_SIZE) >= surfaceDepthAt(column);
    }

    public boolean risesToTheRight() {
        return leftEdgeDepth > rightEdgeDepth;
    }

    public boolean isSteep() {
        return abs(leftEdgeDepth - rightEdgeDepth) == TILE_SPRITE_SIZE;
    }

    public boolean isUphillFor(final boolean movingRight) {
        return risesToTheRight() == movingRight;
    }

    /**
     * @param columnInTile the horizontal position within the tile, in pixels with the fraction, from 0 to 16
     * @return the depth of the straight ramp line there
     */
    public double idealSurfaceDepthAt(final double columnInTile) {
        return leftEdgeDepth + (rightEdgeDepth - leftEdgeDepth) * columnInTile / TILE_SPRITE_SIZE;
    }

    /**
     * @return how hard the slope pulls a player downhill, signed by the downhill direction
     */
    public int playerPull() {
        final int pull = isSteep() ? STEEP_PLAYER_PULL : GENTLE_PLAYER_PULL;
        return risesToTheRight() ? -pull : pull;
    }

    public int enemyUphillWalkSpeed() {
        return isSteep() ? STEEP_ENEMY_UPHILL_WALK_SPEED : GENTLE_ENEMY_UPHILL_WALK_SPEED;
    }
}
