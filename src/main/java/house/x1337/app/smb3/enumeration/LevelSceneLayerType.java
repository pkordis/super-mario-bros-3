package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.Z_STEP_BETWEEN_LAYERS;
import static java.util.stream.Collectors.toUnmodifiableSet;

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
    private final String layerName = initLayerName();
    @Getter(lazy = true)
    private final float z = initZ();

    public final float initZ() {
        return getOrder() * Z_STEP_BETWEEN_LAYERS;
    }

    public String initLayerName() {
        return "Layer-" + name();
    }

    /**
     * The layers drawn in front of the player while it is in the background — the land decorations and
     * everything above them.
     *
     * <p>Unmodifiable because the set is shared: handing out the live collection would let one caller's
     * stray {@code add} silently change how every player is layered.
     */
    public static Set<LevelSceneLayerType> foregroundLayers() {
        return FOREGROUND_LAYERS;
    }

    private static final Set<LevelSceneLayerType> FOREGROUND_LAYERS = Arrays
        .stream(LevelSceneLayerType.values())
        .filter(layer -> layer.getOrder() >= DECORATIONS_LAND.getOrder())
        .collect(toUnmodifiableSet());
}


