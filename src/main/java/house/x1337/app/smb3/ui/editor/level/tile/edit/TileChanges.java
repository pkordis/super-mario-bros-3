package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;

/**
 * What a tile becomes: its type, its description ({@code null} for none), its pixels ({@code null} to keep the
 * tile's own) and the level object it is linked to ({@code null} for none).
 */
public record TileChanges(
    TileType type,
    String description,
    int[] argbData,
    Integer levelObjectId
) {
    public TileChanges(final TileType type, final String description, final int[] argbData) {
        this(type, description, argbData, null);
    }

    public void applyTo(final Tile tile) {
        tile.setType(type);
        tile.setDescription(description);
        if (argbData != null) {
            tile.setArgbData(argbData);
        }
        tile.setLevelObjectId(levelObjectId);
    }
}
