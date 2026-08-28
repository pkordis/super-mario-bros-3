package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.Category.COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.NON_COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.ONE_WAY_PLATFORM;
import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
public enum TileType {
    // Virtual
    NULL("null", VIRTUAL, null),
    RENDERING_STARTER("Rendering Starter", VIRTUAL, null),
    SPAWN_POINT("Spawn Point", VIRTUAL, null),

    // Air
    BACKGROUND_COLOR("Background Color", NON_COLLIDING, AIR),
    CLOUD("Cloud", NON_COLLIDING, DECORATIONS_AIR),
    DOOR("Door", NON_COLLIDING, DECORATIONS_AIR),
    LEVEL_COMPLETION("Level Completion", NON_COLLIDING, DECORATIONS_LAND),
    SHADOW("Shadow", NON_COLLIDING, DECORATIONS_AIR),
    STAR("Star", NON_COLLIDING, DECORATIONS_AIR),

    // Vegetation
    BUSH("Bush", NON_COLLIDING, DECORATIONS_LAND),
    HEDGE("Hedge", NON_COLLIDING, DECORATIONS_LAND),

    // Panel
    PANEL_WALKABLE_TOP("Panel Top (Walkable)", ONE_WAY_PLATFORM, DECORATIONS_LAND),
    PANEL_TRANSPARENT("Panel (Bottom, Left/Right Side, Shadow)", NON_COLLIDING, DECORATIONS_LAND),

    // Solid
    LAKITU_CLOUD("Lakitu Cloud (Walkable)", ONE_WAY_PLATFORM, DECORATIONS_LAND),
    OBJECT_INTERACTIVE_SINGLE("Interactive Object (Single-tiled)", COLLIDING, INTERACTIVE_OBJECTS),
    OBJECT_INTERACTIVE_PART("Interactive Object (Multi-tiled)", COLLIDING, INTERACTIVE_OBJECTS),
    PIPE_TERMINATION_PART("Pipe - Termination", COLLIDING, STATIC_ENVIRONMENT),
    PIPE_BODY_PART("Pipe Body", COLLIDING, STATIC_ENVIRONMENT),
    SOLID("Solid - Flat Ground/Obstacle/Block", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP("Solid - Ramp Ground/Obstacle (Uphill/Downhill)", COLLIDING, STATIC_ENVIRONMENT),

    // Water
    WATER_SURFACE("Water - Surface", NON_COLLIDING, STATIC_ENVIRONMENT),
    WATER_BODY("Water - Body (Swimmable)", NON_COLLIDING, STATIC_ENVIRONMENT);

    private final String label;
    private final Category category;
    private final LevelSceneLayerType typicalLevelSceneLayerOwningType;

    public enum Category {
        COLLIDING,
        NON_COLLIDING,
        ONE_WAY_PLATFORM,
        VIRTUAL
    }

    public static TileType fromLabel(final String label) {
        for (final TileType t : TileType.values()) {
            if (t.getLabel().equals(label)) return t;
        }
        return null;
    }

    public static List<TileType> getByCategory(final Category... categories) {
        final List<Category> list = Arrays.asList(categories);
        return Arrays
            .stream(TileType.values())
            .filter(t -> list.contains(t.category))
            .toList();
    }

    public static List<TileType> getByExcludedCategory(final Category... categories) {
        final List<Category> list = Arrays.asList(categories);
        return Arrays
            .stream(TileType.values())
            .filter(t -> !list.contains(t.category))
            .toList();
    }
}
