package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;

/**
 * Distributes the flat {@code Tile[][]} grid produced by a PNG import across the level-scene layers.
 *
 * <p>Each tile is routed to the layer named by its {@link TileType#getTypicalLevelSceneLayerOwningType()
 * typical owning layer}. Tiles that are not yet classified ({@code type == null}) or whose type has no
 * owning layer (virtual tiles) are left out, so they can be routed later — "as the user goes" — once a
 * type is assigned and {@link #route} is re-run against the same grid.
 *
 * <p>Empty cells are finalised per layer:
 * <ul>
 *   <li>every layer other than {@code AIR} fills its empty cells with {@code NULL_TILE};</li>
 *   <li>the {@code AIR} layer tries to leave no empty (null / {@code NULL_TILE}) cell: it auto-fills
 *       them with the most frequent tile in the layer, but only if that tile is at least
 *       {@value #AIR_DOMINANCE_THRESHOLD_PERCENT}% dominant across the whole matrix; otherwise the
 *       remaining cells fall back to {@code NULL_TILE}.</li>
 * </ul>
 * Layers are returned ordered bottom-to-top by {@link LevelSceneLayerType#getOrder()}.
 */
@Singleton
public final class LevelSceneImportRouter {
    private static final int AIR_DOMINANCE_THRESHOLD_PERCENT = 60;

    public List<LevelSceneLayer> route(final Tile[][] grid, final int rows, final int columns) {
        final Map<LevelSceneLayerType, Tile[][]> matrices = new EnumMap<>(LevelSceneLayerType.class);
        for (final LevelSceneLayerType type : LevelSceneLayerType.values()) {
            matrices.put(type, new Tile[rows][columns]);
        }

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                final Tile tile = (grid != null && grid[row] != null) ? grid[row][column] : null;
                if (tile == null) {
                    continue;
                }
                final TileType type = tile.getType();
                if (type == null) {
                    continue;
                }
                final LevelSceneLayerType owningLayer = type.getTypicalLevelSceneLayerOwningType();
                if (owningLayer == null) {
                    continue;
                }
                matrices.get(owningLayer)[row][column] = tile;
            }
        }

        final List<LevelSceneLayer> layers = new ArrayList<>();
        for (final LevelSceneLayerType type : orderedTypes()) {
            final Tile[][] tiles = matrices.get(type);
            if (type == AIR) {
                fillAirLayer(tiles, rows, columns);
            } else {
                fillEmptyCellsWithNullTile(tiles, rows, columns);
            }
            layers.add(LevelSceneLayer.builder()
                .type(type)
                .visible(true)
                .tiles(tiles)
                .build());
        }
        return layers;
    }

    private static List<LevelSceneLayerType> orderedTypes() {
        final List<LevelSceneLayerType> types = new ArrayList<>(List.of(LevelSceneLayerType.values()));
        types.sort(Comparator.comparingInt(LevelSceneLayerType::getOrder));
        return types;
    }

    private static void fillEmptyCellsWithNullTile(final Tile[][] tiles, final int rows, final int columns) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (tiles[row][column] == null) {
                    tiles[row][column] = NULL_TILE;
                }
            }
        }
    }

    private static void fillAirLayer(final Tile[][] tiles, final int rows, final int columns) {
        final Map<Integer, Integer> countsByTileId = new HashMap<>();
        final Map<Integer, Tile> tilesById = new HashMap<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                final Tile tile = tiles[row][column];
                if (tile != null) {
                    countsByTileId.merge(tile.getId(), 1, Integer::sum);
                    tilesById.putIfAbsent(tile.getId(), tile);
                }
            }
        }

        Tile dominantTile = null;
        int dominantCount = 0;
        for (final Map.Entry<Integer, Integer> entry : countsByTileId.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominantCount = entry.getValue();
                dominantTile = tilesById.get(entry.getKey());
            }
        }

        final int totalCells = rows * columns;
        final boolean dominantEnough =
            dominantTile != null && dominantCount * 100 >= AIR_DOMINANCE_THRESHOLD_PERCENT * totalCells;
        final Tile fillTile = dominantEnough ? dominantTile : NULL_TILE;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (tiles[row][column] == null) {
                    tiles[row][column] = fillTile;
                }
            }
        }
    }
}


