package house.x1337.app.smb3.model.game.player.level.asset.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.BaseLevelScenePlayerAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.util.converter.TextureJsonDeserializer;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static house.x1337.app.smb3.util.converter.TextureJsonDeserializer.SPRITE_LOADER_ATTRIBUTE;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class PlayerAnimatorAssetsLoader {
    private static final ObjectMapper MAPPER = buildMapper();

    public static <AA extends PlayerAnimatorAssets, A extends BaseLevelScenePlayerAnimator<AA>> AA load(
        final Class<AA> type,
        final A animator
    ) {
        final String resourcePath = animator.getFramesParentContext() + "assets.json";
        final Function<String, Texture> spriteLoader = animator::loadSprite;
        try (InputStream input = openResource(resourcePath)) {
            return MAPPER
                .readerFor(type)
                .withAttribute(SPRITE_LOADER_ATTRIBUTE, spriteLoader)
                .readValue(input);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to load animator assets from " + resourcePath, e);
        }
    }

    private static InputStream openResource(final String resourcePath) {
        final InputStream input = PlayerAnimatorAssetsLoader.class
            .getClassLoader()
            .getResourceAsStream(resourcePath);
        if (input == null) {
            throw new UncheckedIOException(new IOException("Resource not found on classpath: " + resourcePath));
        }
        return input;
    }

    private static ObjectMapper buildMapper() {
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(Texture.class, new TextureJsonDeserializer());
        return JsonMapper.builder()
            .addModule(module)
            .visibility(FIELD, ANY)
            .build();
    }
}
