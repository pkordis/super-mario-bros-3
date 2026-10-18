package house.x1337.app.smb3.model.game.motion;

import house.x1337.app.smb3.model.game.Offset;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floor;

public record PixelPosition(double x, double y) {
    public static PixelPosition ofTile(final Offset offset) {
        return new PixelPosition(
            (double) offset.x() * TILE_SPRITE_SIZE,
            (double) offset.y() * TILE_SPRITE_SIZE
        );
    }

    public PixelPosition withX(final double x) {
        return new PixelPosition(x, this.y);
    }

    public PixelPosition withY(final double y) {
        return new PixelPosition(this.x, y);
    }

    public PixelPosition plus(
        final double dx,
        final double dy
    ) {
        return new PixelPosition(x + dx, y + dy);
    }

    public PixelPosition plusX(final double dx) {
        return new PixelPosition(x + dx, y);
    }

    public PixelPosition plusY(final double dy) {
        return new PixelPosition(x, y + dy);
    }

    /**
     * Advances both axes by one frame of the given velocity.
     *
     * @param velocity the 4.4 fixed-point velocity to apply
     * @return the position one frame later
     */
    public PixelPosition stepped(final FixedPointVelocity velocity) {
        return plus(velocity.pixelStepX(), velocity.pixelStepY());
    }

    /**
     * Advances the X axis alone. Not redundant with {@link #stepped(FixedPointVelocity)}: movement
     * code proposes one axis at a time precisely so it can collide that axis before committing to
     * it, leaving the other untouched.
     *
     * @param velocity the 4.4 fixed-point velocity to apply
     * @return the position with only X advanced
     */
    public PixelPosition steppedX(final FixedPointVelocity velocity) {
        return plusX(velocity.pixelStepX());
    }

    /**
     * Advances the Y axis alone.
     *
     * @param velocity the 4.4 fixed-point velocity to apply
     * @return the position with only Y advanced
     */
    public PixelPosition steppedY(final FixedPointVelocity velocity) {
        return plusY(velocity.pixelStepY());
    }

    /**
     * @return the tile column this position falls in, flooring — so a position anywhere inside a
     *         cell reports that cell
     */
    public int column() {
        return (int) floor(x / TILE_SPRITE_SIZE);
    }

    /**
     * @return the tile row this position falls in, flooring
     */
    public int row() {
        return (int) floor(y / TILE_SPRITE_SIZE);
    }
}
