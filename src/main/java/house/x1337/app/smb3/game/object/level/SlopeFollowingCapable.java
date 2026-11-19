package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.max;

/**
 * The terrain probes of an object a tile wide that moves along the ground: it lands on flat ground and on ramps,
 * climbs a ramp and follows it down, and turns at walls. The ground walking enemies and the walking power-ups share
 * it. See {@code kb/slopes.md}, "Enemies and power-ups on a slope".
 *
 * <p>A floor is only detected while the object's tracked Y is less than {@value #FLOOR_DETECTION_DEPTH_LIMIT_PIXELS}
 * pixels into its tile row; see {@code kb/dynamic-entities.md}, the section on the floor-depth limit.
 */
public interface SlopeFollowingCapable extends ActiveLevelObject {
    int BODY_SIZE_PIXELS = TILE_SPRITE_SIZE;
    int FLOOR_DETECTION_DEPTH_LIMIT_PIXELS = 4;
    int MIN_SLOPE_STICK_PIXELS = 2;

    /**
     * Where a descending object comes to rest when it moves to {@code proposed}: on the surface of a ramp under its
     * center, which it also climbs onto and follows down, or on top of a flat tile it is landing on.
     *
     * @param proposed the position the object is about to move to
     * @return the Y to rest at, or empty when there is no ground to land on
     */
    default OptionalDouble findLandingY(final PixelPosition proposed) {
        final int centreX = (int) floor(getPosition().x() + BODY_SIZE_PIXELS / 2.0);
        final double feetY = proposed.y() + BODY_SIZE_PIXELS;
        final int stickPixels = max(MIN_SLOPE_STICK_PIXELS, abs(getVelocity().x()) / TILE_SPRITE_SIZE + 1);
        final OptionalInt rampSurfaceY = getCollisionGrid().findRampSurfaceNear(centreX, feetY, stickPixels);
        if (rampSurfaceY.isPresent()) {
            return OptionalDouble.of(rampSurfaceY.getAsInt() - BODY_SIZE_PIXELS);
        }
        if (isGroundBelow(proposed)) {
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            return OptionalDouble.of((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
        }
        return OptionalDouble.empty();
    }

    /**
     * @param drawPosition where the object is about to be drawn
     * @return how far below that position to draw it, so that it glides along a slope; 0 while carried, as it is then
     *         drawn with its carrier
     */
    default double slopeDrawOffsetPixels(final PixelPosition drawPosition) {
        if (this instanceof final CarriableLevelObject carriable && carriable.isCarried()) {
            return 0;
        }
        return getCollisionGrid().findSlopeDrawOffset(
            drawPosition.x() + BODY_SIZE_PIXELS / 2.0,
            drawPosition.y() + BODY_SIZE_PIXELS
        );
    }

    /**
     * @param proposed the position the object is about to move to
     * @return {@code true} if a flat solid tile sits under its feet there and it is shallow enough into that tile to be
     *         landing on it. Ramps are left to {@link #findLandingY(PixelPosition)}.
     */
    default boolean isGroundBelow(final PixelPosition proposed) {
        final double depthIntoTile = proposed.y() - (double) proposed.row() * TILE_SPRITE_SIZE;
        if (depthIntoTile >= FLOOR_DETECTION_DEPTH_LIMIT_PIXELS) {
            return false;
        }
        final int centerColumn = getPosition().plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
        final Coordinates cell = of(centerColumn, feetRow);
        return getCollisionGrid().isGroundSolidTile(cell) && !getCollisionGrid().isRampAt(cell);
    }

    /**
     * @param proposed the position the object is about to move to
     * @return {@code true} if its leading edge (right when travelling right, left otherwise) would enter a solid tile
     *         there. A ramp, and the ground a ramp leads up onto, is not a wall.
     */
    default boolean isWallAhead(final PixelPosition proposed) {
        final int midRow = getPosition().plusY(BODY_SIZE_PIXELS / 2.0).row();
        final boolean movingRight = getVelocity().movingRight();
        final int leadingColumn = movingRight
            ? proposed.plusX(BODY_SIZE_PIXELS - 1).column()
            : proposed.column();
        final Coordinates cell = of(leadingColumn, midRow);
        return getCollisionGrid().isWallOrCeilingSolidTile(cell)
            && !getCollisionGrid().isClimbableFromTheSide(cell, movingRight);
    }
}
