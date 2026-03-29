package house.x1337.app.smb3.util.normalizer;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord.LevelSceneLayerData;

import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;

/**
 * Enforces the data-driven assumptions of a persisted {@link LevelSceneRecord}: a record may never
 * have a layer missing. After normalization the record's layer map contains an entry for every
 * {@link LevelSceneLayerType} and each layer holds a complete flattened {@code rows * columns} grid.
 * Any gap — a missing layer type or a missing cell — is filled with the {@code NULL_TILE} id
 * ({@code 0}), so an empty layer is represented by an all-zero array rather than {@code null}.
 */
public interface LevelSceneRecordNormalizer {
    default LevelSceneRecord normalize(final LevelSceneRecord record) {
        final int rows = record.getRows();
        final int columns = record.getColumns();
        final Map<String, LevelSceneLayerData> layers = record.getLayers();
        final LevelSceneRecord.LevelSceneRecordBuilder builder = LevelSceneRecord.builder()
            .id(record.getId())
            .title(record.getTitle())
            .description(record.getDescription())
            .rows(rows)
            .columns(columns)
            .updatedAt(record.getUpdatedAt())
            .renderingStarterRow(record.getRenderingStarterRow())
            .renderingStarterColumn(record.getRenderingStarterColumn())
            .spawnPointRow(record.getSpawnPointRow())
            .spawnPointColumn(record.getSpawnPointColumn());
        for (final LevelSceneLayerType type : LevelSceneLayerType.values()) {
            final LevelSceneLayerData existing = (layers != null) ? layers.get(type.name()) : null;
            final int[] tileIds = normalizeTileIds(
                (existing != null) ? existing.getTileIds() : null, rows, columns);
            builder.layer(type.name(), LevelSceneLayerData.builder()
                .type(type)
                .tileIds(tileIds)
                .build());
        }
        return builder.build();
    }

    default int[] normalizeTileIds(final int[] tileIds, final int rows, final int columns) {
        final int length = rows * columns;
        final int[] normalized = new int[length];
        for (int i = 0; i < length; i++) {
            normalized[i] = (tileIds != null && i < tileIds.length)
                    ? tileIds[i]
                    : NULL_TILE.getId();
        }
        return normalized;
    }
}


