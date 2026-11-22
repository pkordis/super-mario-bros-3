package house.x1337.app.smb3.model.game;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.max;
import static java.lang.Math.min;

/** A rectangle in level pixels: its top-left pixel and its size. */
public record PixelArea(
    int x,
    int y,
    int width,
    int height
) {
    public int lastX() {
        return x + width - 1;
    }

    public int lastY() {
        return y + height - 1;
    }

    public boolean contains(final Coordinates pixel) {
        return pixel.x() >= x && pixel.x() <= lastX() && pixel.y() >= y && pixel.y() <= lastY();
    }

    public boolean intersects(final PixelArea other) {
        return x <= other.lastX() && other.x <= lastX() && y <= other.lastY() && other.y <= lastY();
    }

    public Optional<PixelArea> clippedTo(final LevelSceneDimensions levelSceneDimensions) {
        final int firstX = max(x, 0);
        final int firstY = max(y, 0);
        final int endX = min(lastX(), levelSceneDimensions.columns() * TILE_SPRITE_SIZE - 1);
        final int endY = min(lastY(), levelSceneDimensions.rows() * TILE_SPRITE_SIZE - 1);
        if (firstX > endX || firstY > endY) {
            return Optional.empty();
        }
        return Optional.of(new PixelArea(firstX, firstY, endX - firstX + 1, endY - firstY + 1));
    }
}
