package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.ui.tile.Tile;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;

/**
 * An {@link EnemyStamp}'s behaviour — the enemy counterpart of {@code TileCapabilities}.
 *
 * <p>Conversion to and from the stored {@code EnemyRecord} is deliberately <b>not</b> here: rebuilding
 * an enemy's {@code Tile[][]} from stored ids needs a {@code TilesProvider}, which a model object has no
 * business holding. That lives in {@code EnemyConverter}, as {@code LevelSceneConverter} does for a
 * level scene.
 */
public sealed interface EnemyCapabilities permits EnemyStamp {
    /** @return how many rows of parts this enemy is — the grid's own shape, never a stored field */
    default int getRows() {
        final Tile[][] tiles = ((EnemyStamp) this).getTiles();
        return tiles == null ? 0 : tiles.length;
    }

    /** @return how many columns of parts this enemy is */
    default int getColumns() {
        final Tile[][] tiles = ((EnemyStamp) this).getTiles();
        return (tiles == null || tiles.length == 0 || tiles[0] == null) ? 0 : tiles[0].length;
    }

    /**
     * @param cell the grid cell: {@code x} is its column, {@code y} its row
     * @return the part at that cell of the enemy's grid
     */
    default Tile tileAt(final Coordinates cell) {
        return ((EnemyStamp) this).getTiles()[cell.y()][cell.x()];
    }

    /**
     * @return the tile the enemy is anchored by — the cell an author paints to place this enemy in a
     *         level
     */
    default Tile renderingStarterTile() {
        final EnemyStamp enemy = (EnemyStamp) this;
        return tileAt(enemy.getRenderingStarter());
    }

    /**
     * @return the id of the tile the enemy is anchored by — what a level placement is keyed on, since a
     *         level layer stores tile ids
     */
    default int renderingStarterTileId() {
        return renderingStarterTile().getId();
    }

    /** The stamp placed with its rendering starter part on {@code rendererCell}, exactly on the cell. */
    default EnemyPlacement placedAt(final Coordinates rendererCell) {
        return placedAtPixel(of(rendererCell.x() * TILE_SPRITE_SIZE, rendererCell.y() * TILE_SPRITE_SIZE));
    }

    /** The stamp placed with the top-left of its rendering starter part on {@code levelPixel}. */
    default EnemyPlacement placedAtPixel(final Coordinates levelPixel) {
        return new EnemyPlacement(((EnemyStamp) this).getId(), levelPixel.x(), levelPixel.y());
    }

    /** The pixels {@code placement} of this stamp covers: its whole grid, empty parts included. */
    default PixelArea boundsOf(final EnemyPlacement placement) {
        final Coordinates renderingStarter = ((EnemyStamp) this).getRenderingStarter();
        return new PixelArea(
            placement.x() - renderingStarter.x() * TILE_SPRITE_SIZE,
            placement.y() - renderingStarter.y() * TILE_SPRITE_SIZE,
            getColumns() * TILE_SPRITE_SIZE,
            getRows() * TILE_SPRITE_SIZE
        );
    }

    /**
     * @param tileId the tile id to look for
     * @return {@code true} if any of this enemy's parts is that tile — its anchor or any other cell
     */
    default boolean containsTile(final int tileId) {
        for (final Tile[] row : ((EnemyStamp) this).getTiles()) {
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
        final EnemyStamp enemy = (EnemyStamp) this;
        if (enemy.getEnemyType() == null || getRows() == 0 || getColumns() == 0) {
            return false;
        }
        for (final Tile[] row : enemy.getTiles()) {
            if (row == null || row.length != getColumns()) {
                return false;
            }
        }
        final Coordinates renderingStarter = enemy.getRenderingStarter();
        return renderingStarter != null
            && renderingStarter.y() >= 0
            && renderingStarter.y() < getRows()
            && renderingStarter.x() >= 0
            && renderingStarter.x() < getColumns();
    }

    /**
     * @return what the author called this enemy, falling back to the kind of enemy it is
     */
    default String getName() {
        final EnemyStamp enemy = (EnemyStamp) this;
        final String description = enemy.getDescription();
        if (description != null && !description.isBlank()) {
            return description;
        }
        return enemy.getEnemyType().getLabel();
    }
}
