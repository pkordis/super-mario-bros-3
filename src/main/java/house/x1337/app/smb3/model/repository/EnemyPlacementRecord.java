package house.x1337.app.smb3.model.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class EnemyPlacementRecord implements Serializable {
    private String stampId;
    private int x;
    private int y;
}
