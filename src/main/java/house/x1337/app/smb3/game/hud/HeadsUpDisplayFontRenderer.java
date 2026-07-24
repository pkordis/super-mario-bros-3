package house.x1337.app.smb3.game.hud;

import house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.util.GameRenderer;
import jakarta.annotation.PostConstruct;

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
 * {@code resources/font/hud/}. These are extracted directly from the SMB3
 * CHR pattern table banks ($5E/$5F) with the correct NES palette applied:
 * black outlines (color index 1) and white fill (color index 2) on a
 * transparent background.
 *
 * <p>Glyph images are loaded once at startup and cached as ARGB int arrays
 * for fast blitting into the HUD pixel buffer by {@link HeadsUpDisplayRenderer}.
 *
 * <p>The static HUD base image ({@code hud_base.png}) is also loaded here.
 * It provides the pixel-perfect frame, borders, and static text ("WORLD",
 * card slot frames, etc.) from the original example rendering.
 */
public interface HeadsUpDisplayFontRenderer extends GameRenderer {

    /** Cell width in pixels at 1:1 NES resolution. */
    int CELL_WIDTH = 8;

    /** Cell height in pixels at 1:1 NES resolution. */
    int CELL_HEIGHT = 8;

    /** HUD base image width. */
    int HUD_WIDTH = 240;

    /** HUD base image height. */
    int HUD_HEIGHT = 48;

    /** Classpath directory containing the glyph PNGs and base image. */
    String FONT_PATH = "/font/hud/";

    // -------------------------------------------------------------------------
    // Glyph enumeration
    // -------------------------------------------------------------------------

    /** Digit glyphs indexed 0–9 for quick lookup. */
    HeadsUpDisplayGlyph[] DIGIT_HEADS_UP_DISPLAY_GLYPHS = HeadsUpDisplayGlyph.getDigits();

    // -------------------------------------------------------------------------
    // Cached data
    // -------------------------------------------------------------------------

    Map<HeadsUpDisplayGlyph, int[]> GLYPH_CACHE = new EnumMap<>(HeadsUpDisplayGlyph.class);

    ImageResource getBaseImage();

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Loads the HUD base image and all glyph PNGs from the classpath.
     * Must be called once at startup before any rendering occurs.
     */
    @PostConstruct
    default void initFontRenderer() {

        // Load base image
//        loadBaseImage();

        // Load all glyphs
        for (final HeadsUpDisplayGlyph glyph : HeadsUpDisplayGlyph.values()) {
            final String resourcePath = FONT_PATH + glyph.getFilename() + ".png";
            try (final InputStream is = HeadsUpDisplayFontRenderer.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
//                    log.warn("Hud-font glyph missing: {} (expected at {})", glyph, resourcePath);
                    GLYPH_CACHE.put(glyph, new int[CELL_WIDTH * CELL_HEIGHT]);
                    continue;
                }
                final BufferedImage image = ImageIO.read(is);
                final int[] argb = new int[CELL_WIDTH * CELL_HEIGHT];
                image.getRGB(0, 0, CELL_WIDTH, CELL_HEIGHT, argb, 0, CELL_WIDTH);
                GLYPH_CACHE.put(glyph, argb);
            } catch (final IOException e) {
//                log.error("Failed to load hud-font glyph: {}", glyph, e);
                GLYPH_CACHE.put(glyph, new int[CELL_WIDTH * CELL_HEIGHT]);
            }
        }
//        log.info("Hud-font loaded: {} glyphs + base image from {}", GLYPH_CACHE.size(), FONT_PATH);
    }

//    private static void loadBaseImage() {
//        final String path = FONT_PATH + "hud_base.png";
//        try (final InputStream is = HudFontRenderer.class.getResourceAsStream(path)) {
//            if (is == null) {
//                log.error("HUD base image missing: {}", path);
//                baseImage = new int[HUD_WIDTH * HUD_HEIGHT];
//                return;
//            }
//            final BufferedImage image = ImageIO.read(is);
//            baseImage = new int[HUD_WIDTH * HUD_HEIGHT];
//            image.getRGB(0, 0, HUD_WIDTH, HUD_HEIGHT, baseImage, 0, HUD_WIDTH);
//        } catch (final IOException e) {
//            log.error("Failed to load HUD base image", e);
//            baseImage = new int[HUD_WIDTH * HUD_HEIGHT];
//        }
//    }

    // -------------------------------------------------------------------------
    // Glyph access
    // -------------------------------------------------------------------------

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
        return DIGIT_HEADS_UP_DISPLAY_GLYPHS[digit & 0x0F];
    }
}
