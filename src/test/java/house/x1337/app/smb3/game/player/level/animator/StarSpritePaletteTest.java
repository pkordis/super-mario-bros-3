package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ImageRaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static org.assertj.core.api.Assertions.assertThat;

/** The colours the player's sprites take on while the star invincibility cycles. */
class StarSpritePaletteTest {
    private final ColorRGBA RED = rgb(181, 49, 32);
    private final ColorRGBA SKIN = rgb(255, 204, 197);
    private final ColorRGBA BLACK = rgb(0, 0, 0);

    private final StarSpritePalette palette = new StarSpritePalette();
    private final Texture original = sprite();
    private final Node node = nodeShowing(original);

    @Test
    @DisplayName("Palette 0 leaves the sprite as authored")
    void paletteZeroIsTheOriginal() {
        // Execute
        palette.applyTo(node, 0);

        // Verify
        assertThat(shown()).isSameAs(original);
    }

    @Test
    @DisplayName("Palette 1 swaps red, skin and black for black, white and red")
    void paletteOneIsBlackWhiteAndRed() {
        // Execute
        palette.applyTo(node, 1);

        // Verify
        assertThat(pixelsOf(shown())).containsExactly(
            rgb(0, 0, 0), rgb(252, 252, 252), rgb(181, 49, 32)
        );
    }

    @Test
    @DisplayName("Palette 2 swaps red, skin and black for black, white and green")
    void paletteTwoIsBlackWhiteAndGreen() {
        // Execute
        palette.applyTo(node, 2);

        // Verify
        assertThat(pixelsOf(shown())).containsExactly(
            rgb(0, 0, 0), rgb(252, 252, 252), rgb(76, 220, 72)
        );
    }

    @Test
    @DisplayName("Palette 3 swaps red and black for black and amber, and keeps the skin")
    void paletteThreeIsBlackSkinAndAmber() {
        // Execute
        palette.applyTo(node, 3);

        // Verify
        assertThat(pixelsOf(shown())).containsExactly(
            rgb(0, 0, 0), rgb(255, 204, 197), rgb(252, 152, 56)
        );
    }

    @Test
    @DisplayName("Going from one palette to another is made from the original, and palette 0 gives it back")
    void switchingAlwaysStartsFromTheOriginal() {
        // Execute
        palette.applyTo(node, 1);
        palette.applyTo(node, 3);
        final Texture third = shown();
        palette.applyTo(node, 0);

        // Verify
        assertThat(pixelsOf(third)[2]).isEqualTo(rgb(252, 152, 56));
        assertThat(shown()).isSameAs(original);
    }

    @Test
    @DisplayName("A recoloured copy is made once and reused")
    void aVariantIsReused() {
        // Execute
        palette.applyTo(node, 1);
        final Texture first = shown();
        palette.applyTo(node, 2);
        palette.applyTo(node, 1);

        // Verify
        assertThat(shown()).isSameAs(first);
    }

    @Test
    @DisplayName("Transparent pixels stay transparent")
    void transparentPixelsAreKept() {
        // Execute
        palette.applyTo(node, 1);

        // Verify
        final ColorRGBA corner = new ColorRGBA();
        ImageRaster.create(shown().getImage()).getPixel(3, 0, corner);
        assertThat(corner.a).isZero();
    }

    private Texture shown() {
        final Geometry geometry = (Geometry) node.getChild(0);
        return geometry.getMaterial().getTextureParam("ColorMap").getTextureValue();
    }

    private ColorRGBA[] pixelsOf(final Texture texture) {
        final ImageRaster raster = ImageRaster.create(texture.getImage());
        final ColorRGBA[] pixels = new ColorRGBA[3];
        for (int x = 0; x < pixels.length; x++) {
            pixels[x] = raster.getPixel(x, 0);
            pixels[x] = rgb(
                Math.round(pixels[x].r * 255),
                Math.round(pixels[x].g * 255),
                Math.round(pixels[x].b * 255)
            );
        }
        return pixels;
    }

    private Texture sprite() {
        final Image image = new Image(RGBA8, 4, 1, createByteBuffer(4 * 4), sRGB);
        final ImageRaster raster = ImageRaster.create(image);
        raster.setPixel(0, 0, RED);
        raster.setPixel(1, 0, SKIN);
        raster.setPixel(2, 0, BLACK);
        raster.setPixel(3, 0, new ColorRGBA(0, 0, 0, 0));
        return new Texture2D(image);
    }

    private Node nodeShowing(final Texture texture) {
        final Material material = new Material(new DesktopAssetManager(true), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        final Geometry geometry = new Geometry("Player", new Quad(1, 1));
        geometry.setMaterial(material);
        final Node holder = new Node("PlayerNode");
        holder.attachChild(geometry);
        return holder;
    }

    private ColorRGBA rgb(final int red, final int green, final int blue) {
        return new ColorRGBA(red / 255f, green / 255f, blue / 255f, 1f);
    }
}
