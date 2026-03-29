package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
public enum LevelSceneLayerType {
    AIR(0, "Air"),
    DECORATIONS_AIR(1, "Decorations (Air)"),
    DECORATIONS_LAND(2, "Decorations (Land)"),
    INTERACTIVE_OBJECTS(4, "Interactive Objects"),
    NON_PLAYABLE_CHARACTERS(5, "Non-Playable Characters (NPCs)"),
    STATIC_ENVIRONMENT(3, "Static Environment");

    private final int order;
    private final String label;
}


