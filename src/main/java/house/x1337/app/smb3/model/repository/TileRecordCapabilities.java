package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.model.ui.tile.Tile;

public sealed interface TileRecordCapabilities permits TileRecord {
    default Tile toTile() {
        final TileRecord record = (TileRecord) this;
        return Tile.builder()
            .id(record.getId())
            .sha256(record.getSha256())
            .type(record.getType())
            .description(record.getDescription())
            .originalArgbData(record.getOriginalArgbData())
            .argbData(record.getArgbData())
            .build();
    }
}
