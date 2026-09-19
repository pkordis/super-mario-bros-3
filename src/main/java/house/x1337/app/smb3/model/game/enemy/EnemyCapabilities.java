package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.ui.tile.Tile;

/**
 * An {@link Enemy}'s behaviour — the enemy counterpart of {@code TileCapabilities}.
 *
 * <p>Conversion to and from the stored {@code EnemyRecord} is deliberately <b>not</b> here: rebuilding
 * an enemy's {@code Tile[][]} from stored ids needs a {@code TilesProvider}, which a model object has no
 * business holding. That lives in {@code EnemyConverter}, as {@code LevelSceneConverter} does for a
 * level scene.
 */
public sealed interface EnemyCapabilities permits Enemy {
    /** @return how many rows of parts this enemy is — the grid's own shape, never a stored field */
    default int getRows() {
        final Tile[][] tiles = ((Enemy) this).getTiles();
        return tiles == null ? 0 : tiles.length;
    }

    /** @return how many columns of parts this enemy is */
    default int getColumns() {
        final Tile[][] tiles = ((Enemy) this).getTiles();
        return (tiles == null || tiles.length == 0 || tiles[0] == null) ? 0 : tiles[0].length;
    }

    /**
     * @param row    grid row
     * @param column grid column
     * @return the part at that cell of the enemy's grid
     */
    default Tile tileAt(final int row, final int column) {
        return ((Enemy) this).getTiles()[row][column];
    }

    /**
     * @return the tile the enemy is anchored by — the cell an author paints to place this enemy in a
     *         level
     */
    default Tile renderingStarterTile() {
        final Enemy enemy = (Enemy) this;
        return tileAt(enemy.getRenderingStarterRow(), enemy.getRenderingStarterColumn());
    }

    /**
     * @return the id of the tile the enemy is anchored by — what a level placement is keyed on, since a
     *         level layer stores tile ids
     */
    default int renderingStarterTileId() {
        return renderingStarterTile().getId();
    }

    /**
     * @param tileId the tile id to look for
     * @return {@code true} if any of this enemy's parts is that tile — its anchor or any other cell
     */
    default boolean containsTile(final int tileId) {
        for (final Tile[] row : ((Enemy) this).getTiles()) {
            for (final Tile part : row) {
                if (part != null && part.getId() == tileId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether this enemy can actually be drawn and placed: a non-empty, rectangular grid of parts with
     * its anchor inside it.
     *
     * <p>Checked before the editor renders or stamps an enemy, and before the spawner places one: an
     * enemy is read back from a database and may predate a change, have been written by hand, or have
     * lost fields, and one unusable document must not be able to stop the editor from opening.
     */
    default boolean isWellFormed() {
        final Enemy enemy = (Enemy) this;
        if (enemy.getEnemyType() == null || getRows() == 0 || getColumns() == 0) {
            return false;
        }
        for (final Tile[] row : enemy.getTiles()) {
            if (row == null || row.length != getColumns()) {
                return false;
            }
        }
        return enemy.getRenderingStarterRow() >= 0
            && enemy.getRenderingStarterRow() < getRows()
            && enemy.getRenderingStarterColumn() >= 0
            && enemy.getRenderingStarterColumn() < getColumns();
    }

    /**
     * @return what the author called this enemy, falling back to the kind of enemy it is
     */
    default String getName() {
        final Enemy enemy = (Enemy) this;
        final String description = enemy.getDescription();
        if (description != null && !description.isBlank()) {
            return description;
        }
        return enemy.getEnemyType().getLabel();
    }
}
