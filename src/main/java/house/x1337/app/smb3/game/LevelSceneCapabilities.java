package house.x1337.app.smb3.game;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static java.util.Comparator.comparingInt;

public sealed interface LevelSceneCapabilities permits LevelScene {
    /**
     * Returns every environment layer ordered bottom-to-top by its
     * {@link LevelSceneLayerType#getOrder() order} (lowest first). The renderer draws them in this
     * sequence so a higher-order layer is painted over the ones beneath it, with transparent pixels
     * letting the lower layers show through. Layer visibility is intentionally ignored — when a scene
     * is rendered, every layer is always stacked.
     */
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
        final List<LevelScene.LevelSceneLayer> layers = getLayersBottomToTop();
        final Tile[][] composite = new Tile[getRows()][getColumns()];
        for (final Tile[] row : composite) {
            Arrays.fill(row, NULL_TILE);
        }
        for (final LevelScene.LevelSceneLayer layer : layers) {
            final Tile[][] tiles = layer.getTiles();
            for (int row = 0; row < getRows(); row++) {
                for (int col = 0; col < getColumns(); col++) {
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

    int getRows();
    int getColumns();
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
            return getType().getLabel();
        }
    }
}
