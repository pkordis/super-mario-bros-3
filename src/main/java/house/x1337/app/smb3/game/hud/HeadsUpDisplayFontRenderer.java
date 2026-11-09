package house.x1337.app.smb3.game.hud;

import house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.util.GameRenderer;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.DefaultResourceLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Image-backed HUD font ("hud-font") that loads glyph PNGs from the classpath.
 *
 * <p>Each glyph is an 8×8 pixel RGBA PNG stored under
 * {@code resources/hud/}. Each one has black outlines (color index 1) and
 * white fill (color index 2) on a transparent background.
 *
 * <p>Glyph images are loaded once at startup and cached as ARGB int arrays
 * for fast blitting into the HUD pixel buffer by {@link HeadsUpDisplayRenderer}.
 *
 * <p>The static HUD base image ({@code hud_base.png}) is also loaded here.
 * It provides the pixel-perfect frame, borders, and static text ("WORLD",
 * card slot frames, etc.).
 */
public interface HeadsUpDisplayFontRenderer extends GameRenderer {

    /** Cell width in pixels at 1:1 resolution. */
    int CELL_WIDTH = 8;

    /** Cell height in pixels at 1:1 resolution. */
    int CELL_HEIGHT = 8;

    /** HUD base image width. */
    int HUD_WIDTH = 240;

    /** HUD base image height. */
    int HUD_HEIGHT = 48;

    /** Classpath directory containing the glyph PNGs and base image. */
    String FONT_PATH = "/hud/";

    /** Width and height of a status-bar card: a 2 x 2 block of cells. */
    int CARD_SIZE = 16;

    Map<HeadsUpDisplayGlyph, int[]> GLYPH_CACHE = new EnumMap<>(HeadsUpDisplayGlyph.class);
    Map<LevelCompletionCardType, int[]> CARD_CACHE = new EnumMap<>(LevelCompletionCardType.class);
    HeadsUpDisplayGlyph[] DIGIT_HEADS_UP_DISPLAY_GLYPHS = HeadsUpDisplayGlyph.getDigits();

    ImageResource getBaseImage();

    @PostConstruct
    default void initFontRenderer() {
        // Load all glyphs
        for (final HeadsUpDisplayGlyph glyph : HeadsUpDisplayGlyph.values()) {
            final String resourcePath = FONT_PATH + glyph.getFilename() + ".png";
            try (final InputStream is = HeadsUpDisplayFontRenderer.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    GLYPH_CACHE.put(glyph, new int[CELL_WIDTH * CELL_HEIGHT]);
                    continue;
                }
                final BufferedImage image = ImageIO.read(is);
                final int[] argb = new int[CELL_WIDTH * CELL_HEIGHT];
                image.getRGB(0, 0, CELL_WIDTH, CELL_HEIGHT, argb, 0, CELL_WIDTH);
                GLYPH_CACHE.put(glyph, argb);
            } catch (final IOException e) {
                GLYPH_CACHE.put(glyph, new int[CELL_WIDTH * CELL_HEIGHT]);
            }
        }
        loadStatusBarCards();
    }

    /**
     * Returns the cached ARGB pixel data of a status-bar card.
     *
     * @param card the card to retrieve
     * @return 256-element ARGB array (16×16 row-major), never null; transparent when the image is missing
     */
    default int[] pixels(final LevelCompletionCardType card) {
        final int[] data = CARD_CACHE.get(card);
        return data != null ? data : new int[CARD_SIZE * CARD_SIZE];
    }

    private static void loadStatusBarCards() {
        final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        for (final LevelCompletionCardType card : LevelCompletionCardType.values()) {
            final int[] argb = new int[CARD_SIZE * CARD_SIZE];
            try (final InputStream is = resourceLoader.getResource(card.getStatusBarPath()).getInputStream()) {
                final BufferedImage image = ImageIO.read(is);
                if (image != null) {
                    image.getRGB(0, 0, CARD_SIZE, CARD_SIZE, argb, 0, CARD_SIZE);
                }
            } catch (final IOException e) {
                // Leave the card transparent, as a missing glyph is: the empty box shows through.
            }
            CARD_CACHE.put(card, argb);
        }
    }

    /**
     * Returns the cached ARGB pixel data for the given glyph.
     *
     * @param glyph the glyph to retrieve
     * @return 64-element ARGB array (8×8 row-major), never null
     */
    default int[] pixels(final HeadsUpDisplayGlyph glyph) {
        final int[] data = GLYPH_CACHE.get(glyph);
        return data != null ? data : new int[CELL_WIDTH * CELL_HEIGHT];
    }

    /**
     * Returns the {@link HeadsUpDisplayGlyph} enum for a digit value (0–9).
     *
     * @param digit digit value 0–9
     * @return the corresponding Glyph enum constant
     */
    default HeadsUpDisplayGlyph digitGlyph(final int digit) {
        return DIGIT_HEADS_UP_DISPLAY_GLYPHS[digit & 15];
    }
}
