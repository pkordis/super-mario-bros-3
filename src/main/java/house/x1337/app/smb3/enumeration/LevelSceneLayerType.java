package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.Z_STEP_BETWEEN_LAYERS;

@Getter
@RequiredArgsConstructor
public enum LevelSceneLayerType {
    AIR(0, "Air"),
    DECORATIONS_AIR(1, "Decorations (Air)"),
    DECORATIONS_LAND(2, "Decorations (Land)"),
    INTERACTIVE_OBJECTS(4, "Interactive Objects"),
    NON_PLAYABLE_CHARACTERS(5, "Non-Playable Characters (NPCs)"),
    STATIC_ENVIRONMENT(3, "Static Environment");

    private final int order;
    private final String label;
    @Getter(lazy = true)
    private final float z = initZ();

    public final float initZ() {
        return getOrder() * Z_STEP_BETWEEN_LAYERS;
    }
}


