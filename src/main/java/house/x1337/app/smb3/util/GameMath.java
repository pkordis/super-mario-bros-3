package house.x1337.app.smb3.util;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.clamp;
import static java.lang.Math.floor;

public interface GameMath {
    /**
     * Euclidean modulo 16 (works correctly for negative numbers).
     */
    default double tileModulo(final double n) {
        return n - floor(n / TILE_SPRITE_SIZE) * TILE_SPRITE_SIZE;
    }

    default int clampDigit(final int value) {
        return clamp(value, 0, 9);
    }
}
