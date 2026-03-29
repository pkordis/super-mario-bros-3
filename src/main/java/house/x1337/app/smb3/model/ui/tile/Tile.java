package house.x1337.app.smb3.model.ui.tile;

import house.x1337.app.smb3.enumeration.TileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class Tile implements TileCapabilities {
    private int id;
    private String sha256;
    private TileType type;
    private String description;
    private int[] originalArgbData;
    private int[] argbData;
}
