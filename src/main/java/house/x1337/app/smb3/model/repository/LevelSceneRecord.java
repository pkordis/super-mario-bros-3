package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Id;

import java.io.Serializable;
import java.util.Map;

@Entity(
    value = "levelScenes"
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public final class LevelSceneRecord implements Serializable {
    @Id
    private String id;
    private String title;
    private String description;
    private int rows;
    private int columns;
    private long updatedAt;
    private Integer renderingStarterRow;
    private Integer renderingStarterColumn;
    private Integer spawnPointRow;
    private Integer spawnPointColumn;
    @Singular
    private Map<String, LevelSceneLayerData> layers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class LevelSceneLayerData implements Serializable {
        private LevelSceneLayerType type;
        private int[] tileIds;
    }
}
