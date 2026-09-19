package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@Prototype
@NoArgsConstructor
@AllArgsConstructor
public final class Enemy implements EnemyCapabilities {
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String description;
    private EnemyType enemyType;
    private Tile[][] tiles;
    private int renderingStarterRow;
    private int renderingStarterColumn;
    private long updatedAt;
}
