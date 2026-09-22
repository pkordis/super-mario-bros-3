package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.repository.EnemyRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.extractor.TilesExtractor;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;

/**
 * Converts an enemy between its domain shape ({@link EnemyStamp}, holding resolved {@link Tile}s) and its
 * stored shape ({@link EnemyRecord}, holding a flat {@code int[]} of tile ids plus the grid's
 * dimensions) — the enemy counterpart of {@link LevelSceneLayerConverter}.
 *
 * <p>The asymmetry is the same as a level layer's: flattening down needs nothing but the grid
 * ({@link TilesExtractor#extractTileIds}), while building up needs a {@code TilesProvider} to turn each
 * stored id back into a tile. That is why conversion lives here rather than as a method on either model,
 * and why the implementor — {@code EnemyService} — is the one that supplies the provider.
 */
public interface EnemyConverter extends TilesExtractor {
    /**
     * Rebuilds an enemy from its stored document, resolving every part id through the tiles provider. An
     * id the tiles collection no longer holds becomes {@code NULL_TILE} rather than {@code null}, so the
     * grid never has gaps — the same tolerance {@code toLevelSceneLayer} applies to a level's layers.
     */
    default EnemyStamp toEnemy(final EnemyRecord record) {
        return EnemyStamp.builder()
            .id(record.getId())
            .description(record.getDescription())
            .enemyType(record.getEnemyType())
            .tiles(toTileGrid(record.getTileIds(), record.getRows(), record.getColumns()))
            .renderingStarterRow(record.getRenderingStarterRow())
            .renderingStarterColumn(record.getRenderingStarterColumn())
            .updatedAt(record.getUpdatedAt())
            .build();
    }

    /**
     * Flattens an enemy back into the document it is stored as. {@code rows} and {@code columns} are read
     * off the grid, which is the only place they exist on the domain model.
     */
    default EnemyRecord toEnemyRecord(final EnemyStamp enemy) {
        return EnemyRecord.builder()
            .id(enemy.getId())
            .description(enemy.getDescription())
            .enemyType(enemy.getEnemyType())
            .rows(enemy.getRows())
            .columns(enemy.getColumns())
            .tileIds(extractTileIds(enemy.getTiles()))
            .renderingStarterRow(enemy.getRenderingStarterRow())
            .renderingStarterColumn(enemy.getRenderingStarterColumn())
            .updatedAt(enemy.getUpdatedAt())
            .build();
    }

    /**
     * Resolves a flat, row-major run of tile ids into a {@code rows x columns} grid of tiles. Also used
     * when an enemy is first created, to turn the ids the freshly imported parts were stored under into
     * the grid the new {@link EnemyStamp} carries.
     *
     * @param tileIds row-major ids, {@code tileIds[row * columns + column]}; may be {@code null}
     * @param rows    how many rows the grid has
     * @param columns how many columns the grid has
     * @return a fully populated grid; missing or unknown ids become {@code NULL_TILE}
     */
    default Tile[][] toTileGrid(final int[] tileIds, final int rows, final int columns) {
        final Tile[][] tiles = new Tile[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                final int index = row * columns + column;
                final int id = (tileIds != null && index < tileIds.length)
                    ? tileIds[index]
                    : NULL_TILE.getId();
                tiles[row][column] = getTilesProvider().findById(id).orElse(NULL_TILE);
            }
        }
        return tiles;
    }
}
