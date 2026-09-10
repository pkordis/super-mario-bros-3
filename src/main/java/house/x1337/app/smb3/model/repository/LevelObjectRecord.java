package house.x1337.app.smb3.model.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Id;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Entity(
    value = "levelObjects"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class LevelObjectRecord implements Serializable, LevelObjectRecordCapabilities {
    @Id
    private int id;
    private String type;
    private String description;
    @Default
    private Map<String, Object> data = new HashMap<>();
}
