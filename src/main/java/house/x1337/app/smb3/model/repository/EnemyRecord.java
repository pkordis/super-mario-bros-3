package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Id;

import java.io.Serializable;

@Entity(
    value = "enemies"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class EnemyRecord implements Serializable {
    @Id
    private String id;
    private String description;
    private EnemyType enemyType;
    private int rows;
    private int columns;
    private int[] tileIds;
    private int renderingStarterRow;
    private int renderingStarterColumn;
    private long updatedAt;
}
