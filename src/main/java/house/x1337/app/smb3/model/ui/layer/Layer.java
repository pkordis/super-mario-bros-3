package house.x1337.app.smb3.model.ui.layer;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Layer {
    private final LevelSceneLayerType type;
    private boolean visible = true;
    private boolean enabled = true;
}
