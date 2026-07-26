package house.x1337.app.smb3.model.game;

import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;

public record LevelObjectOffset(int x, int y) implements Offset {
    public static LevelObjectOffset fromPlayerOffset(
        final Player player,
        final Offset offset
    ) {
        final PlayerPosition position = player.getPosition();
        final int sx = (int) floor(position.getX()) + offset.x();
        final int sy = (int) floor(position.getY()) + offset.y();
        return new LevelObjectOffset(
            floorDiv(sx, TILE_SPRITE_SIZE),
            floorDiv(sy, TILE_SPRITE_SIZE)
        );
    }

    public boolean isOutsideOf(final CollisionGrid collisionGrid) {
        final LevelSceneDimensions dimensions = collisionGrid.getDimensions();
        return y < 0 ||
            y >= dimensions.rows() ||
            x < 0 ||
            x >= dimensions.columns();
    }
}
