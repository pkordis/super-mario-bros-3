package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;

/**
 * What the tile form holds for a tile: its type, its description ({@code null} for none) and its pixels
 * ({@code null} to keep the tile's own).
 */
public record TileChanges(
    TileType type,
    String description,
    int[] argbData
) {
    public void applyTo(final Tile tile) {
        tile.setType(type);
        tile.setDescription(description);
        if (argbData != null) {
            tile.setArgbData(argbData);
        }
    }
}
