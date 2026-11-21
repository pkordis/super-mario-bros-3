package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.Coordinates.of;

/** An enemy stamp placed in a layer, its rendering starter part on {@code rendererCell}. */
public record EnemyPlacement(EnemyStamp stamp, Coordinates rendererCell) {
    /** The stamp's whole grid around the renderer cell, empty parts included. */
    public MultiTileArea bounds() {
        final Coordinates renderingStarter = stamp.getRenderingStarter();
        return new MultiTileArea(
            rendererCell.x() - renderingStarter.x(),
            rendererCell.y() - renderingStarter.y(),
            stamp.getColumns(),
            stamp.getRows()
        );
    }

    /**
     * The layer cells that still hold this placement's parts. A part that was painted over, or that falls off the
     * layer, owns nothing, so clearing these cells never touches anything else.
     */
    public List<Coordinates> heldCells(final Tile[][] layer) {
        final List<Coordinates> cells = new ArrayList<>();
        final MultiTileArea bounds = bounds();
        for (int partRow = 0; partRow < stamp.getRows(); partRow++) {
            for (int partColumn = 0; partColumn < stamp.getColumns(); partColumn++) {
                final Tile part = stamp.tileAt(of(partColumn, partRow));
                final Coordinates cell = of(bounds.column() + partColumn, bounds.row() + partRow);
                final Tile held = tileAt(layer, cell);
                if (isPartOf(part) && held != null && held.getId() == part.getId()) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    /** Whether every part that falls inside the layer is still in place. */
    public boolean isWhole(final Tile[][] layer) {
        int partsInLayer = 0;
        final MultiTileArea bounds = bounds();
        for (int partRow = 0; partRow < stamp.getRows(); partRow++) {
            for (int partColumn = 0; partColumn < stamp.getColumns(); partColumn++) {
                final Coordinates cell = of(bounds.column() + partColumn, bounds.row() + partRow);
                if (isPartOf(stamp.tileAt(of(partColumn, partRow))) && isInside(layer, cell)) {
                    partsInLayer++;
                }
            }
        }
        return heldCells(layer).size() == partsInLayer;
    }

    private boolean isPartOf(final Tile tile) {
        return tile != null && !tile.isVirtual();
    }

    private Tile tileAt(
        final Tile[][] layer,
        final Coordinates cell
    ) {
        return isInside(layer, cell) ? layer[cell.y()][cell.x()] : null;
    }

    private boolean isInside(
        final Tile[][] layer,
        final Coordinates cell
    ) {
        return cell.y() >= 0 && cell.y() < layer.length && cell.x() >= 0 && cell.x() < layer[cell.y()].length;
    }
}
