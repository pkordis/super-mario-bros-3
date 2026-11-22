package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static java.lang.Math.max;
import static java.lang.Math.min;

public interface LevelSceneTileCapabilities extends LevelSceneLayerCapabilities {
    default Tile[][] emptyTiles(
        final int rows,
        final int cols
    ) {
        final Tile[][] t = new Tile[rows][cols];
        for (final Tile[] row : t) {
            Arrays.fill(row, NULL_TILE);
        }
        return t;
    }

    default Tile[][] shiftedTiles(
        final Tile[][] tiles,
        final int newColumns,
        final int newRows,
        final int rowShift
    ) {
        final LevelSceneDimensions levelSceneDimensions = getLevelSceneDimensions();
        final Tile[][] shifted = emptyTiles(newRows, newColumns);
        for (int row = max(0, rowShift); row < newRows; row++) {
            final int sourceRow = row - rowShift;
            if (sourceRow >= levelSceneDimensions.rows()) {
                break;
            }
            System.arraycopy(tiles[sourceRow], 0, shifted[row], 0, min(levelSceneDimensions.columns(), newColumns));
        }
        return shifted;
    }

    /** Whether the tab was built from a PNG import whose grid holds the tile. */
    default boolean importsTile(final int tileId) {
        final Tile[][] importGrid = getImportGrid();
        if (importGrid == null) {
            return false;
        }
        for (final Tile[] row : importGrid) {
            for (final Tile cell : row) {
                if (cell != null && cell.getId() == tileId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Moves each cell holding the tile in a layer other than {@code target} into the same cell of {@code target},
     * overwriting what that cell held, and clears it where it was.
     */
    default void moveTile(final Tile tile, final LevelSceneLayerType target) {
        final List<LevelSceneLayer> layers = getLayers();
        final Optional<LevelSceneLayer> targetLayer = layers.stream()
            .filter(layer -> layer.getType() == target)
            .findFirst();
        if (targetLayer.isEmpty()) {
            return;
        }
        final Tile[][] targetTiles = targetLayer.get().getTiles();
        for (final LevelSceneLayer layer : layers) {
            if (layer.getType() == target) {
                continue;
            }
            final Tile[][] tiles = layer.getTiles();
            for (int row = 0; row < tiles.length; row++) {
                for (int column = 0; column < tiles[row].length; column++) {
                    if (tiles[row][column] != null && tiles[row][column].getId() == tile.getId()) {
                        tiles[row][column] = NULL_TILE;
                        targetTiles[row][column] = tile;
                    }
                }
            }
        }
        getLevelSceneEditorGrid().repaint();
    }

    default int countTileCellsOutside(final int tileId, final LevelSceneLayerType target) {
        int cells = 0;
        for (final LevelSceneLayer layer : getLayers()) {
            if (layer.getType() == target) {
                continue;
            }
            for (final Tile[] row : layer.getTiles()) {
                for (final Tile cell : row) {
                    if (cell != null && cell.getId() == tileId) {
                        cells++;
                    }
                }
            }
        }
        return cells;
    }

    /**
     * Refreshes the rendering of a tile whose pixel data changed (e.g. after classification),
     * invalidating its cached image and repainting the grid.
     */
    default void refreshTile(final Tile tile) {
        final LevelSceneEditorGrid levelSceneEditorGrid = getLevelSceneEditorGrid();
        levelSceneEditorGrid.invalidateTile(tile.getId());
        levelSceneEditorGrid.repaint();
    }
}
