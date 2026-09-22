package house.x1337.app.smb3.model.game.asset.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import house.x1337.app.smb3.model.game.asset.AnimatorAssets;
import house.x1337.app.smb3.model.game.enemy.asset.GoombaAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.NormalAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.RaccoonAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkToNormalAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.SuitLostPoofAnimatorAssets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static house.x1337.app.smb3.model.game.effect.PoofSequence.POOF_FRAMES_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code assets.json} must name exactly the components of the record it feeds.
 *
 * <p>{@link AnimatorAssetsLoader} binds the two by <b>field name</b> ({@code visibility(FIELD, ANY)}),
 * and Jackson ignores a key it cannot place — so renaming a record component without renaming its JSON
 * key leaves the component silently null and the sprite silently missing, with nothing failing to
 * compile and no exception at load. Only the Goomba bundle is otherwise deserialized by a test, and the
 * player bundles need a live jME asset manager, so this reflection comparison is what covers them.
 */
class AnimatorAssetsDescriptorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> descriptors() {
        return Stream.of(
            Arguments.of(GoombaAnimatorAssets.class, "sprites/enemy/goomba/normal/"),
            Arguments.of(NormalAnimatorAssets.class, "sprites/player/mario/level/normal/"),
            Arguments.of(RaccoonAnimatorAssets.class, "sprites/player/mario/level/raccoon/"),
            Arguments.of(ShrunkAnimatorAssets.class, "sprites/player/mario/level/shrunk/"),
            Arguments.of(ShrunkToNormalAnimatorAssets.class, "sprites/player/mario/level/shrunk_to_normal/"),
            Arguments.of(SuitLostPoofAnimatorAssets.class, POOF_FRAMES_CONTEXT)
        );
    }

    @ParameterizedTest(name = "{0} <-> {1}assets.json")
    @MethodSource("descriptors")
    @DisplayName("The descriptor's keys are exactly the record's components")
    void descriptorKeysMatchRecordComponents(
        final Class<? extends AnimatorAssets> type,
        final String framesContext
    ) throws IOException {
        // Prepare
        final List<String> componentNames = componentNamesOf(type);

        // Execute
        final List<String> descriptorKeys = List.copyOf(readDescriptor(framesContext).keySet());

        // Verify - neither a component the descriptor never fills, nor a key nothing reads
        assertThat(descriptorKeys).containsExactlyInAnyOrderElementsOf(componentNames);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptors")
    @DisplayName("No asset name carries a redundant Texture or Frame token")
    void noComponentNameMentionsRedundantTokens(
        final Class<? extends AnimatorAssets> type,
        final String framesContext
    ) throws IOException {
        // An asset name says which action it depicts, nothing else. The component's own type already says
        // Texture or Texture[], and every one of these is a frame, so neither token distinguishes
        // anything: "walkFrameTextures" carried two words of noise over "walk".
        assertThat(componentNamesOf(type))
            .as("record components of %s", type.getSimpleName())
            .allSatisfy(name -> assertThat(name.toLowerCase()).doesNotContain("texture", "frame"));
        assertThat(readDescriptor(framesContext).keySet())
            .as("keys of %sassets.json", framesContext)
            .allSatisfy(key -> assertThat(key.toLowerCase()).doesNotContain("texture", "frame"));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static List<String> componentNamesOf(final Class<? extends AnimatorAssets> type) {
        assertThat(type.isRecord()).as("%s is a record", type.getSimpleName()).isTrue();
        return Arrays
            .stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readDescriptor(final String framesContext) throws IOException {
        final String resourcePath = framesContext + "assets.json";
        try (InputStream input = AnimatorAssetsDescriptorTest.class
            .getClassLoader()
            .getResourceAsStream(resourcePath)) {
            assertThat(input).as("%s exists on the classpath", resourcePath).isNotNull();
            return MAPPER.readValue(input, Map.class);
        }
    }
}
