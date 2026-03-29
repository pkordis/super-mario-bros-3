package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord.LevelSceneLayerData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.extractor.TilesExtractor;

import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;

public interface LevelSceneLayerConverter extends TilesExtractor {
    /**
     * Builds a runtime layer for the given {@code type}, always producing a fully populated
     * {@code rows x columns} grid. A missing layer ({@code data == null}), a missing row or a missing
     * cell — as well as any unknown tile id — is filled with {@code NULL_TILE}, so the resulting
     * layer never has gaps and never has a {@code null} type.
     */
    default LevelSceneLayer toLevelSceneLayer(
        final LevelSceneLayerType type,
        final LevelSceneLayerData data,
        final int rows,
        final int columns
    ) {
        final int[] tileIds = (data != null) ? data.getTileIds() : null;
        final Tile[][] tiles = new Tile[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                final int index = r * columns + c;
                final int id = (tileIds != null && index < tileIds.length)
                        ? tileIds[index]
                        : NULL_TILE.getId();
                tiles[r][c] = getTilesProvider().findById(id).orElse(NULL_TILE);
            }
        }
        return LevelSceneLayer.builder()
            .type(type)
            .visible(true)
            .tiles(tiles)
            .build();
    }


    default LevelSceneLayer toLevelSceneLayer(
        final LevelSceneLayerType layerType,
        final LevelSceneRecord levelSceneRecord
    ) {
        final Map<String, LevelSceneLayerData> layers = levelSceneRecord.getLayers();
        final LevelSceneLayerData layerData = (layers != null) ? layers.get(layerType.name()) : null;
        return toLevelSceneLayer(
            layerType,
            layerData,
            levelSceneRecord.getRows(),
            levelSceneRecord.getColumns()
        );
    }

    default LevelSceneLayerData toLayerData(final LevelSceneLayerType type, final LevelSceneLayer layer) {
        return LevelSceneLayerData.builder()
            .type(type)
            .tileIds((layer != null) ? extractTileIds(layer.getTiles()) : null)
            .build();
    }
}
