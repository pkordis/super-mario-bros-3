package house.x1337.app.smb3.model.game;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.max;
import static java.lang.Math.min;

/** A rectangle of scene tiles: its top-left cell and its size in cells. */
public record MultiTileArea(
    int column,
    int row,
    int columns,
    int rows
) {
    public MultiTileArea(final Coordinates cell) {
        this(cell.x(), cell.y(), 1, 1);
    }

    public int lastColumn() {
        return column + columns - 1;
    }

    public int lastRow() {
        return row + rows - 1;
    }

    public boolean contains(final Coordinates cell) {
        return cell.x() >= column && cell.x() <= lastColumn() && cell.y() >= row && cell.y() <= lastRow();
    }

    public PixelArea toPixelArea() {
        return new PixelArea(
            column * TILE_SPRITE_SIZE,
            row * TILE_SPRITE_SIZE,
            columns * TILE_SPRITE_SIZE,
            rows * TILE_SPRITE_SIZE
        );
    }

    public boolean intersects(final MultiTileArea other) {
        return column <= other.lastColumn()
            && other.column <= lastColumn()
            && row <= other.lastRow()
            && other.row <= lastRow();
    }

    public Optional<MultiTileArea> clippedTo(final LevelSceneDimensions levelSceneDimensions) {
        final int firstColumn = max(column, 0);
        final int firstRow = max(row, 0);
        final int endColumn = min(lastColumn(), levelSceneDimensions.columns() - 1);
        final int endRow = min(lastRow(), levelSceneDimensions.rows() - 1);
        if (firstColumn > endColumn || firstRow > endRow) {
            return Optional.empty();
        }
        return Optional.of(new MultiTileArea(
            firstColumn,
            firstRow,
            endColumn - firstColumn + 1,
            endRow - firstRow + 1
        ));
    }
}
