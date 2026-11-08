package house.x1337.app.smb3.model.game;

import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

import java.io.Serializable;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;
import static java.lang.Math.round;

public record Coordinates(int x, int y) implements Serializable {
    public static Coordinates of(final int x, final int y) {
        return new Coordinates(x, y);
    }

    public static Coordinates of(final float x, final float y) {
        return of(round(x), round(y));
    }

    public static Coordinates of(final double x, final double y) {
        return of(round(x), round(y));
    }

    public static Coordinates fromPlayerOffset(
        final Player player,
        final Coordinates offset
    ) {
        final PlayerPosition position = player.getPosition();
        final int sx = (int) floor(position.getX()) + offset.x();
        final int sy = (int) floor(position.getY()) + offset.y();
        return new Coordinates(
            floorDiv(sx, TILE_SPRITE_SIZE),
            floorDiv(sy, TILE_SPRITE_SIZE)
        );
    }
}
