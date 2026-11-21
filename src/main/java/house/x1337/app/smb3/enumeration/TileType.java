package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.model.game.collision.SlopeProfile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.Category.COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.ENEMY;
import static house.x1337.app.smb3.enumeration.TileType.Category.NON_COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.ONE_WAY_PLATFORM;
import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.*;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_FALLING_LOW;
import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
public enum TileType {
    // Virtual
    NULL("null", VIRTUAL, null),

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
    SOLID("Solid Flat Ground/Obstacle/Block", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP_STEEP_RISING("Solid Ramp - Steep rising right", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP_STEEP_FALLING("Solid Ramp - Steep falling right", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP_GENTLE_RISING_LOW("Solid Ramp - Gentle rising right (Low Half)", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP_GENTLE_RISING_HIGH("Solid Ramp - Gentle rising right (High Half)", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP_GENTLE_FALLING_HIGH("Solid Ramp - Gentle falling right (High Half)", COLLIDING, STATIC_ENVIRONMENT),
    SOLID_RAMP_GENTLE_FALLING_LOW("Solid Ramp - Gentle falling right (Low Half)", COLLIDING, STATIC_ENVIRONMENT),

    // Enemy
    ENEMY_PART("Enemy (Single or Multi-tiled)", ENEMY, NON_PLAYABLE_CHARACTERS),

    // Water
    WATER_SURFACE("Water - Surface", NON_COLLIDING, STATIC_ENVIRONMENT),
    WATER_BODY("Water - Body (Swimmable)", NON_COLLIDING, STATIC_ENVIRONMENT);

    private final String label;
    private final Category category;
    private final LevelSceneLayerType levelSceneLayerOwningType;

    public enum Category {
        COLLIDING,
        ENEMY,
        NON_COLLIDING,
        ONE_WAY_PLATFORM,
        VIRTUAL
    }

    /**
     * Whether a tile of this type may be painted while {@code activeLayer} is the active one.
     *
     * <p>A tile belongs to exactly one layer, and painting it anywhere else is the authoring mistake this
     * guards: brick blocks dropped into the static-environment layer, for instance, are terrain rather
     * than interactive objects. The null type, the empty cell, owns no layer, so it is always available.
     *
     * @param activeLayer the layer currently being edited, may be {@code null} when no scene is open
     * @return {@code true} when this type is free to paint
     */
    public boolean isPaintableOn(final LevelSceneLayerType activeLayer) {
        return levelSceneLayerOwningType == null || levelSceneLayerOwningType == activeLayer;
    }

    public boolean isInteractiveObject() {
        return this == OBJECT_INTERACTIVE_SINGLE || this == OBJECT_INTERACTIVE_PART;
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

    public Optional<SlopeProfile> findSlopeProfile() {
        return Optional.ofNullable(switch (this) {
            case SOLID_RAMP_STEEP_RISING -> STEEP_RISING;
            case SOLID_RAMP_STEEP_FALLING -> STEEP_FALLING;
            case SOLID_RAMP_GENTLE_RISING_LOW -> GENTLE_RISING_LOW;
            case SOLID_RAMP_GENTLE_RISING_HIGH -> GENTLE_RISING_HIGH;
            case SOLID_RAMP_GENTLE_FALLING_HIGH -> GENTLE_FALLING_HIGH;
            case SOLID_RAMP_GENTLE_FALLING_LOW -> GENTLE_FALLING_LOW;
            default -> null;
        });
    }
}
