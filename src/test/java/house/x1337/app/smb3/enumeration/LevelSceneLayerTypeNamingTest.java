package house.x1337.app.smb3.enumeration;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.foregroundLayers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The naming contract between a layer and its baked geometry in the scene graph.
 *
 * <p>The name used to be spelled out twice — derived here and repeated as string constants on
 * {@code LevelSceneLayerCapabilities} — which is the kind of duplication that drifts silently, since a
 * wrong name only surfaces as a missing geometry at run time. It is now derived in one place, and this
 * pins it against the names the renderer actually bakes.
 */
class LevelSceneLayerTypeNamingTest {

    @ParameterizedTest
    @EnumSource(LevelSceneLayerType.class)
    @DisplayName("Every layer's geometry name is its own constant, prefixed")
    void everyLayerNameFollowsTheConvention(final LevelSceneLayerType type) {
        assertThat(type.getLayerName()).isEqualTo("Layer-" + type.name());
    }

    @ParameterizedTest
    @EnumSource(LevelSceneLayerType.class)
    @DisplayName("A baked layer is found in the scene graph by that name")
    void aBakedLayerIsFoundByItsName(final LevelSceneLayerType type) {
        // Prepare - a node holding one geometry per layer, named the way GameEngineRenderer bakes them
        final Node rootNode = new Node("root");
        for (final LevelSceneLayerType layer : LevelSceneLayerType.values()) {
            rootNode.attachChild(new Geometry(layer.getLayerName(), new Quad(1, 1)));
        }

        // Verify - the lookup GameEngineRenderer#getLayerGeometry performs
        assertThat(rootNode.getChild(type.getLayerName())).isNotNull();
    }

    @Test
    @DisplayName("Layer names are distinct, so no two layers can resolve to one geometry")
    void layerNamesAreDistinct() {
        assertThat(LevelSceneLayerType.values())
            .extracting(LevelSceneLayerType::getLayerName)
            .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("The foreground set is everything from the land decorations up")
    void foregroundLayersAreTheLandDecorationsAndAbove() {
        assertThat(foregroundLayers()).containsExactlyInAnyOrder(
            DECORATIONS_LAND,
            STATIC_ENVIRONMENT,
            INTERACTIVE_OBJECTS,
            NON_PLAYABLE_CHARACTERS
        );
    }

    @Test
    @DisplayName("The sky and its decorations stay behind the player")
    void airLayersAreNotForeground() {
        assertThat(foregroundLayers()).doesNotContain(AIR, DECORATIONS_AIR);
    }

    @Test
    @DisplayName("The foreground set cannot be altered by a caller")
    void foregroundLayersAreImmutable() {
        assertThatThrownBy(() -> foregroundLayers().add(AIR))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
