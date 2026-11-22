package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.ui.tile.Tile;

import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_PIPE_RIGHT;
import static house.x1337.app.smb3.model.game.Coordinates.of;

/**
 * Whether a stamp may be placed where {@code placement} puts it, against the static environment tiles. Only the
 * Goomba pipe has rules: exactly on a cell, beside a pipe termination tile and not over pipe body. Every other enemy
 * goes anywhere. See kb/editor.md, "Goomba pipe placement".
 */
public interface EnemyApplicationRules {
    EnemyType getEnemyType();

    default boolean validateApplication(
        final Tile[][] staticEnvironmentTiles,
        final EnemyPlacement placement
    ) {
        final EnemyType enemyType = getEnemyType();
        if (enemyType.isPlacedOnTheGrid() && !placement.offsetInCell().equals(of(0, 0))) {
            return false;
        }
        return enemyType != GOOMBA_PIPE_RIGHT
            || isBesideAPipeTermination(staticEnvironmentTiles, placement.rendererCell());
    }

    private boolean isBesideAPipeTermination(
        final Tile[][] staticEnvironmentTiles,
        final Coordinates cell
    ) {
        return !isOfType(staticEnvironmentTiles, cell, PIPE_BODY_PART)
            && (isOfType(staticEnvironmentTiles, of(cell.x() - 1, cell.y()), PIPE_TERMINATION_PART)
            || isOfType(staticEnvironmentTiles, of(cell.x() + 1, cell.y()), PIPE_TERMINATION_PART));
    }

    private boolean isOfType(
        final Tile[][] tiles,
        final Coordinates cell,
        final TileType type
    ) {
        if (tiles == null || cell.y() < 0 || cell.y() >= tiles.length || tiles[cell.y()] == null) {
            return false;
        }
        final Tile[] row = tiles[cell.y()];
        if (cell.x() < 0 || cell.x() >= row.length) {
            return false;
        }
        return row[cell.x()] != null && row[cell.x()].isOfType(type);
    }
}
