package house.x1337.app.smb3.model.game;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;
import static java.lang.Math.round;

@Getter
@ToString
@Prototype
@EqualsAndHashCode
@Accessors(fluent = true)
public class Coordinates implements Serializable {
    private int x;
    private int y;

    public static Coordinates of(final int x, final int y) {
        final Coordinates coordinates = new Coordinates();
        coordinates.x = x;
        coordinates.y = y;
        return coordinates;
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
        return of(
            floorDiv(sx, TILE_SPRITE_SIZE),
            floorDiv(sy, TILE_SPRITE_SIZE)
        );
    }

    public Coordinates getOneBelow() {
        return of(x(), y() + 1);
    }
}
