package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.annotation.Singleton;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import static java.lang.Math.round;

/**
 * Draws the player's sprites in the colours the star invincibility cycles through. The sprites have three colours;
 * palette 1 swaps them for black, white and red, palette 2 for black, white and green and palette 3 for black, skin
 * and amber. Palette 0 is the sprite as authored. A recoloured copy of a texture is made once and kept. See
 * kb/star.md, "Colours".
 */
@Singleton
public final class StarSpritePalette {
    private static final int PALETTES = 4;
    private static final int COLOUR_CHANNELS = 255;

    private static final int[] PRIMARY = {181, 49, 32};
    private static final int[] SECONDARY = {255, 204, 197};
    private static final int[] OUTLINE = {0, 0, 0};

    private static final int[] BLACK = {0, 0, 0};
    private static final int[] WHITE = {252, 252, 252};
    private static final int[] RED = {181, 49, 32};
    private static final int[] GREEN = {76, 220, 72};
    private static final int[] AMBER = {252, 152, 56};

    private static final int[][][] REPLACEMENTS = {
        {},
        {BLACK, WHITE, RED},
        {BLACK, WHITE, GREEN},
        {BLACK, SECONDARY, AMBER}
    };

    private final Map<Texture, Texture[]> variantsByOriginal = new IdentityHashMap<>();
    private final Map<Texture, Texture> originalsByVariant = new IdentityHashMap<>();

    /**
     * Puts every sprite of the node in the given palette.
     *
     * @param node    the node holding the player's sprite quads
     * @param palette 0 for the sprites as authored, 1 to 3 for the star's colours
     */
    public void applyTo(
        final Node node,
        final int palette
    ) {
        for (final Spatial child : node.getChildren()) {
            if (child instanceof final Geometry geometry) {
                applyTo(geometry.getMaterial(), palette);
            }
        }
    }

    private void applyTo(
        final Material material,
        final int palette
    ) {
        final MatParamTexture parameter = material.getTextureParam("ColorMap");
        if (parameter == null) {
            return;
        }
        final Texture current = parameter.getTextureValue();
        final Texture original = originalsByVariant.getOrDefault(current, current);
        final Texture wanted = palette == 0 ? original : variantsOf(original)[palette];
        if (wanted != current) {
            material.setTexture("ColorMap", wanted);
        }
    }

    private Texture[] variantsOf(final Texture original) {
        return variantsByOriginal.computeIfAbsent(original, this::buildVariants);
    }

    private Texture[] buildVariants(final Texture original) {
        final Texture[] variants = new Texture[PALETTES];
        variants[0] = original;
        for (int palette = 1; palette < PALETTES; palette++) {
            variants[palette] = recoloured(original, REPLACEMENTS[palette]);
            originalsByVariant.put(variants[palette], original);
        }
        return variants;
    }

    private Texture recoloured(
        final Texture original,
        final int[][] replacements
    ) {
        final Image source = original.getImage();
        final Image image = new Image(
            source.getFormat(),
            source.getWidth(),
            source.getHeight(),
            BufferUtils.clone(source.getData(0)),
            source.getColorSpace()
        );
        final ImageRaster raster = ImageRaster.create(image);
        final ColorRGBA pixel = new ColorRGBA();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                raster.getPixel(x, y, pixel);
                if (pixel.a > 0) {
                    replace(pixel, replacements);
                    raster.setPixel(x, y, pixel);
                }
            }
        }
        final Texture2D texture = new Texture2D(image);
        texture.setMagFilter(original.getMagFilter());
        texture.setMinFilter(original.getMinFilter());
        texture.setWrap(Texture.WrapAxis.S, original.getWrap(Texture.WrapAxis.S));
        texture.setWrap(Texture.WrapAxis.T, original.getWrap(Texture.WrapAxis.T));
        return texture;
    }

    private void replace(
        final ColorRGBA pixel,
        final int[][] replacements
    ) {
        final int[] source = {channel(pixel.r), channel(pixel.g), channel(pixel.b)};
        final int[][] colours = {PRIMARY, SECONDARY, OUTLINE};
        for (int colour = 0; colour < colours.length; colour++) {
            if (Arrays.equals(source, colours[colour])) {
                final int[] target = replacements[colour];
                pixel.set(
                    target[0] / (float) COLOUR_CHANNELS,
                    target[1] / (float) COLOUR_CHANNELS,
                    target[2] / (float) COLOUR_CHANNELS,
                    pixel.a
                );
                return;
            }
        }
    }

    private int channel(final float value) {
        return (int) round(value * COLOUR_CHANNELS);
    }
}
