package house.x1337.app.smb3.util.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.jme3.texture.Texture;

import java.io.IOException;
import java.util.function.Function;

/**
 * Converts a JSON string (a sprite filename) into a loaded {@link Texture}.
 *
 * <p>Because turning a filename into a texture requires the engine's asset
 * manager, the actual loading function is supplied per-read as a context
 * attribute under {@link #SPRITE_LOADER_ATTRIBUTE} (see the animator asset
 * loader). Registering this deserializer for {@link Texture} lets Jackson map
 * both {@code String} → {@code Texture} and {@code String[]} → {@code Texture[]}
 * automatically, keying each model field by its own name.
 */
public class TextureJsonDeserializer extends JsonDeserializer<Texture> {
    public static final String SPRITE_LOADER_ATTRIBUTE = "spriteLoader";

    @Override
    @SuppressWarnings("unchecked")
    public Texture deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
        final String filename = parser.getValueAsString();
        final Function<String, Texture> spriteLoader =
            (Function<String, Texture>) context.getAttribute(SPRITE_LOADER_ATTRIBUTE);
        if (spriteLoader == null) {
            throw new IllegalStateException(
                "No sprite loader bound under attribute '" + SPRITE_LOADER_ATTRIBUTE + "'"
            );
        }
        return spriteLoader.apply(filename);
    }
}
