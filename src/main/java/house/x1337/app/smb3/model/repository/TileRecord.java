package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.TileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Id;

import java.io.Serializable;

@Entity(
    value = "tiles"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class TileRecord implements TileRecordCapabilities, Serializable {
    @Id
    private int id;
    private String sha256;
    private TileType type;
    private String description;
    private int[] originalArgbData;
    private int[] argbData;
}

