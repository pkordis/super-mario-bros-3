package house.x1337.app.smb3.game.level.completion;

import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floorDiv;

@RequiredArgsConstructor
final class CardFlight {
    static final int Y_VELOCITY = -40;

    private final int startY;
    private int ticks;

    int y() {
        return startY + floorDiv(Y_VELOCITY * ticks, TILE_SPRITE_SIZE);
    }

    boolean isAboveTheTopOf(final double screenTop) {
        return y() + TILE_SPRITE_SIZE < screenTop;
    }

    void advance() {
        ticks++;
    }
}
