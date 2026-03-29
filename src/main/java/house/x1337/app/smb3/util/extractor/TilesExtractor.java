package house.x1337.app.smb3.util.extractor;

import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.provider.TilesProvider;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;

public interface TilesExtractor {
    TilesProvider getTilesProvider();

    default int[] extractTileIds(final Tile[][] tiles) {
        if (tiles == null) {
            return null;
        }
        final int rows = tiles.length;
        final int columns = (rows > 0 && tiles[0] != null) ? tiles[0].length : 0;
        final int[] ids = new int[rows * columns];
        for (int r = 0; r < rows; r++) {
            final Tile[] row = tiles[r];
            for (int c = 0; c < columns; c++) {
                final int id = (row != null && c < row.length && row[c] != null)
                        ? row[c].getId()
                        : NULL_TILE.getId();
                ids[r * columns + c] = id;
            }
        }
        return ids;
    }
}
