package house.x1337.app.smb3.model.game;

import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;
import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
public class TileOffset {
    private final int x;
    private final int y;

    public static TileOffset fromPlayerOffset(
        final Player player,
        final Offset offset
    ) {
        final PlayerPosition position = player.getPosition();
        final int sx = (int) floor(position.getX()) + offset.x();
        final int sy = (int) floor(position.getY()) + offset.y();
        return new TileOffset(
            floorDiv(sx, TILE_SPRITE_SIZE),
            floorDiv(sy, TILE_SPRITE_SIZE)
        );
    }
}
