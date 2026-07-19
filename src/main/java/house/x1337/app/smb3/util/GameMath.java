package house.x1337.app.smb3.util;

import static java.lang.Math.floor;

public interface GameMath {
    /**
     * Euclidean modulo 16 (works correctly for negative numbers).
     */
    default double mod16(final double n) {
        return n - floor(n / 16.0) * 16.0;
    }
}
