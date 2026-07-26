package house.x1337.app.smb3.game;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.collision.CollisionGridCapabilities;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static java.util.Comparator.comparingInt;

public sealed interface LevelSceneCapabilities extends CollisionGridCapabilities permits LevelScene {
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
        String AIR = "Layer-AIR";
        String DECORATIONS_AIR = "Layer-DECORATIONS_AIR";
        String DECORATIONS_LAND = "Layer-DECORATIONS_LAND";
        String INTERACTIVE_OBJECTS = "Layer-INTERACTIVE_OBJECTS";
        String NON_PLAYABLE_CHARACTERS = "Layer-NON_PLAYABLE_CHARACTERS";
        String STATIC_ENVIRONMENT = "Layer-STATIC_ENVIRONMENT";

        // Layers that should appear IN FRONT of the player when in BACKGROUND
        String[] FOREGROUND_LAYERS = {
            DECORATIONS_LAND,
            INTERACTIVE_OBJECTS,
            NON_PLAYABLE_CHARACTERS,
            STATIC_ENVIRONMENT
        };

        LevelSceneLayerType getType();
        boolean isVisible();
        Tile[][] getTiles();

        default String getName() {
            return "Layer-" + getType().name();
        }
    }
}
