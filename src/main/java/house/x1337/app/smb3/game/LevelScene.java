package house.x1337.app.smb3.game;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
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
public final class LevelScene implements LevelSceneCapabilities {
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String title;
    private String description;
    private int rows;
    private int columns;
    private long updatedAt;
    private Integer renderingStarterRow;
    private Integer renderingStarterColumn;
    private Integer spawnPointRow;
    private Integer spawnPointColumn;

    private LevelSceneLayer airLayer;
    private LevelSceneLayer airDecorationsLayer;
    private LevelSceneLayer landDecorationsLayer;
    private LevelSceneLayer staticEnvironmentLayer;
    private LevelSceneLayer interactiveObjectsLayer;
    private LevelSceneLayer nonPlayableCharactersLayer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class LevelSceneLayer implements LevelSceneLayerCapabilities {
        private LevelSceneLayerType type;
        private boolean visible;
        private Tile[][] tiles;
    }
}
