package house.x1337.app.smb3.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which layer each tile type belongs on, and the rule the Tiles palette gates itself with.
 *
 * <p>The rule exists to catch an authoring mistake with consequences that only show up at play time: a
 * brick block painted into the static-environment layer is terrain, so nothing treats it as breakable and
 * the player can hit it forever.
 */
class TileTypeOwningLayerTest {

    @ParameterizedTest
    @EnumSource(TileType.class)
    @DisplayName("Every type that paints into the scene declares the layer it belongs on")
    void everyPaintingTypeDeclaresItsLayer(final TileType type) {
        if (type.getCategory() == VIRTUAL) {
            assertThat(type.getLevelSceneLayerOwningType())
                .as("%s marks the scene rather than painting a layer", type)
                .isNull();
        } else {
            assertThat(type.getLevelSceneLayerOwningType())
                .as("%s has to know where it belongs", type)
                .isNotNull();
        }
    }

    @Test
    @DisplayName("Interactive objects belong on the interactive-objects layer, not with the terrain")
    void interactiveObjectsBelongOnTheInteractiveLayer() {
        // The mistake this guards against, stated as an assertion: a brick block is an interactive object,
        // and painted as terrain instead nothing treats it as breakable.
        assertThat(TileType.OBJECT_INTERACTIVE_SINGLE.getLevelSceneLayerOwningType())
            .isEqualTo(INTERACTIVE_OBJECTS);
        assertThat(TileType.OBJECT_INTERACTIVE_SINGLE.isPaintableOn(STATIC_ENVIRONMENT))
            .as("a brick must not be paintable into the terrain layer")
            .isFalse();
        assertThat(TileType.OBJECT_INTERACTIVE_SINGLE.isPaintableOn(INTERACTIVE_OBJECTS)).isTrue();
        assertThat(TileType.OBJECT_INTERACTIVE_PART.getLevelSceneLayerOwningType())
            .isEqualTo(INTERACTIVE_OBJECTS);
    }

    @Test
    @DisplayName("Terrain belongs on the static-environment layer")
    void terrainBelongsOnTheStaticEnvironmentLayer() {
        assertThat(TileType.SOLID.getLevelSceneLayerOwningType()).isEqualTo(STATIC_ENVIRONMENT);
        assertThat(TileType.SOLID.isPaintableOn(STATIC_ENVIRONMENT)).isTrue();
        assertThat(TileType.SOLID.isPaintableOn(INTERACTIVE_OBJECTS)).isFalse();
    }

    @Test
    @DisplayName("Enemy parts belong on the NPC layer")
    void enemyPartsBelongOnTheNpcLayer() {
        assertThat(TileType.ENEMY_PART.getLevelSceneLayerOwningType()).isEqualTo(NON_PLAYABLE_CHARACTERS);
        assertThat(TileType.ENEMY_PART.isPaintableOn(AIR)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TileType.class)
    @DisplayName("A type is paintable on its own layer and nowhere else")
    void aTypeIsPaintableOnlyOnItsOwnLayer(final TileType type) {
        final LevelSceneLayerType owningLayer = type.getLevelSceneLayerOwningType();
        if (owningLayer == null) {
            return;
        }
        assertThat(type.isPaintableOn(owningLayer)).as("%s on its own layer", type).isTrue();
        for (final LevelSceneLayerType other : LevelSceneLayerType.values()) {
            if (other != owningLayer) {
                assertThat(type.isPaintableOn(other)).as("%s on %s", type, other).isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = TileType.class, names = {"NULL", "RENDERING_STARTER", "SPAWN_POINT"})
    @DisplayName("Virtual types are never gated, including when no scene is open")
    void virtualTypesAreNeverGated(final TileType type) {
        for (final LevelSceneLayerType layer : LevelSceneLayerType.values()) {
            assertThat(type.isPaintableOn(layer)).as("%s on %s", type, layer).isTrue();
        }
        assertThat(type.isPaintableOn(null)).as("%s with no scene open", type).isTrue();
    }

    @Test
    @DisplayName("With no active layer nothing that paints is available")
    void nothingPaintableWithoutAnActiveLayer() {
        assertThat(TileType.OBJECT_INTERACTIVE_SINGLE.isPaintableOn(null)).isFalse();
        assertThat(TileType.SOLID.isPaintableOn(null)).isFalse();
    }
}
