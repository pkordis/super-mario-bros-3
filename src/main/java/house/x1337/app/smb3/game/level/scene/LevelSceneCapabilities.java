package house.x1337.app.smb3.game.level.scene;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.collision.ActiveObjectGridCapabilities;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGridCapabilities;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static java.util.Comparator.comparingInt;

public sealed interface LevelSceneCapabilities
    extends
        ActiveObjectGridCapabilities,
        LevelSceneMotionCapabilities,
        StaticEnvironmentCollisionGridCapabilities
    permits
        LevelScene {
    default void tick() {
        getLevelSceneVibration().tick();
    }

    default void reset() {
        getLevelSceneVibration().reset();
    }

    default List<LevelScene.LevelSceneLayer> getLayersBottomToTop() {
        return Stream.of(
                getAirLayer(),
                getAirDecorationsLayer(),
                getLandDecorationsLayer(),
                getStaticEnvironmentLayer(),
                getInteractiveObjectsLayer(),
                getNonPlayableCharactersLayer()
            )
            .filter(Objects::nonNull)
            .sorted(comparingInt(layer -> layer.getType().getOrder()))
            .toList();
    }

    default Tile[][] getTilesOfConsolidatedLayers() {
        return consolidate(getLayersBottomToTop());
    }

    default Tile[][] getTilesOfLayersBelow(final LevelSceneLayerType type) {
        return consolidateBelow(type).surface();
    }

    /**
     * Consolidates the layers below {@code type} while keeping what each cell would fall back to.
     *
     * <p>Consolidation is lossy — a higher layer's tile hides whatever shares its cell — so retiring a
     * tile at runtime (brick broken, coin collected) needs to know what was underneath it. Walking
     * bottom-to-top, every cell the current layer claims demotes its previous occupant to the fallback,
     * which leaves {@code surface} holding the topmost tile and {@code underlay} the one directly beneath
     * it, per cell.
     *
     * <p>Note the fallback is <em>whatever is below that cell's own winning layer</em>, not whatever is
     * below a fixed layer. Anchoring it to a fixed layer is wrong for a tile painted below that anchor:
     * the tile then appears in both views, so retiring it merely re-exposes itself — an indestructible
     * brick that keeps handing out fragments every time it is hit.
     *
     * @param type the exclusive upper bound; layers at or above this order take no part
     * @return the topmost tile per cell, the tile directly beneath it, and which layer each came from
     */
    default ConsolidatedLayers consolidateBelow(final LevelSceneLayerType type) {
        final int rows = getDimensions().rows();
        final int columns = getDimensions().columns();
        final Tile[][] surface = nullTileGrid(rows, columns);
        final Tile[][] underlay = nullTileGrid(rows, columns);
        final LevelSceneLayerType[][] surfaceLayers = new LevelSceneLayerType[rows][columns];
        final LevelSceneLayerType[][] underlayLayers = new LevelSceneLayerType[rows][columns];

        for (final LevelScene.LevelSceneLayer layer : getLayersBottomToTop()) {
            if (layer.getType().getOrder() >= type.getOrder()) {
                continue;
            }
            final Tile[][] tiles = layer.getTiles();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    final Tile tile = tiles[row][col];
                    if (!tile.isRenderable()) {
                        continue;
                    }
                    underlay[row][col] = surface[row][col];
                    underlayLayers[row][col] = surfaceLayers[row][col];
                    surface[row][col] = tile;
                    surfaceLayers[row][col] = layer.getType();
                }
            }
        }
        return new ConsolidatedLayers(surface, underlay, surfaceLayers, underlayLayers);
    }

    private Tile[][] nullTileGrid(final int rows, final int columns) {
        final Tile[][] grid = new Tile[rows][columns];
        for (final Tile[] row : grid) {
            Arrays.fill(row, NULL_TILE);
        }
        return grid;
    }

    /**
     * One consolidation pass: what each cell shows, what it falls back to, and where each came from.
     *
     * @param surface       the topmost tile per cell
     * @param underlay      the tile directly beneath the surface one, {@code NULL_TILE} where there is none
     * @param surfaceLayers the layer each surface tile came from, {@code null} where the cell is empty
     * @param underlayLayers the layer each underlay tile came from, {@code null} where there is none
     */
    record ConsolidatedLayers(
        Tile[][] surface,
        Tile[][] underlay,
        LevelSceneLayerType[][] surfaceLayers,
        LevelSceneLayerType[][] underlayLayers
    ) {
    }

    private Tile[][] consolidate(final List<LevelScene.LevelSceneLayer> layers) {
        final int rows = getDimensions().rows();
        final int columns = getDimensions().columns();
        final Tile[][] composite = new Tile[rows][columns];
        for (final Tile[] row : composite) {
            Arrays.fill(row, NULL_TILE);
        }
        for (final LevelScene.LevelSceneLayer layer : layers) {
            final Tile[][] tiles = layer.getTiles();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    final Tile tile = tiles[row][col];
                    if (tile.isRenderable()) {
                        composite[row][col] = tile;
                    }
                }
            }
        }
        return composite;
    }

    /**
     * Resolves the layer associated with the given {@link LevelSceneLayerType}. This keeps the
     * mapping between a layer type and its backing field in a single place so that callers can treat
     * the scene's layers in a data-driven way (e.g. iterating over {@link LevelSceneLayerType#values()}).
     */
    default LevelScene.LevelSceneLayer getLayer(final LevelSceneLayerType type) {
        return switch (type) {
            case AIR -> getAirLayer();
            case DECORATIONS_AIR -> getAirDecorationsLayer();
            case DECORATIONS_LAND -> getLandDecorationsLayer();
            case STATIC_ENVIRONMENT -> getStaticEnvironmentLayer();
            case INTERACTIVE_OBJECTS -> getInteractiveObjectsLayer();
            case NON_PLAYABLE_CHARACTERS -> getNonPlayableCharactersLayer();
        };
    }

    LevelSceneDimensions getDimensions();
    LevelScene.LevelSceneLayer getAirLayer();
    LevelScene.LevelSceneLayer getAirDecorationsLayer();
    LevelScene.LevelSceneLayer getLandDecorationsLayer();
    LevelScene.LevelSceneLayer getStaticEnvironmentLayer();
    LevelScene.LevelSceneLayer getInteractiveObjectsLayer();
    LevelScene.LevelSceneLayer getNonPlayableCharactersLayer();

    interface LevelSceneLayerCapabilities {
        LevelSceneLayerType getType();
        boolean isVisible();
        Tile[][] getTiles();

        default String getName() {
            return getType().getLayerName();
        }
    }
}
