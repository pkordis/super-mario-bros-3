package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.enumeration.BoldFontGlyph;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.Message;
import house.x1337.app.smb3.util.GameRenderer;
import org.springframework.core.io.DefaultResourceLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static java.lang.Math.max;

/**
 * Paints what the level completion has written on the screen into a {@value #SCREEN_WIDTH} x {@value #SCREEN_HEIGHT}
 * picture of the level view, top row first, transparent wherever nothing is written.
 *
 * <p>The texts are written in {@value #CELL_SIZE} x {@value #CELL_SIZE} cells of the view with the bold font
 * ({@link BoldFontGlyph}): each character is one cell, white on a transparent background, so the level shows
 * through around the letters.
 *
 * <ul>
 *     <li>"COURSE CLEAR !" ({@link Message#COURSE_CLEAR}'s text) starts at cell ({@value #COURSE_CLEAR_COLUMN},
 *         {@value #COURSE_CLEAR_ROW}).</li>
 *     <li>"YOU GOT A CARD" ({@link Message#YOU_GOT_A_CARD}'s text) starts at cell ({@value #YOU_GOT_A_CARD_COLUMN},
 *         {@value #YOU_GOT_A_CARD_ROW}), with the box beside it: a {@value #BOX_SIZE} x {@value #BOX_SIZE} picture
 *         from cell ({@value #BOX_COLUMN}, {@value #BOX_ROW}), the card filling its inside (the status bar's picture
 *         of the card, at ({@value #CARD_X}, {@value #CARD_Y})). Blank cells fill the gap between the text and the
 *         box; a text too long for the gap runs under the box.</li>
 * </ul>
 *
 * <p>"YOU GOT A CARD" comes after "COURSE CLEAR !", which stays: the later message shows both.
 */
public interface LevelCompletionMessageRenderer extends GameRenderer {
    int BOX_COLUMN = 22;
    int BOX_ROW = 6;
    int BOX_SIZE = 32;
    int CARD_SIZE = 16;
    int CELL_SIZE = 8;
    int CARD_X = (BOX_COLUMN + 1) * CELL_SIZE;
    int CARD_Y = (BOX_ROW + 1) * CELL_SIZE;
    int COURSE_CLEAR_COLUMN = 9;
    int COURSE_CLEAR_ROW = 4;
    int SCREEN_HEIGHT = 192;
    int SCREEN_WIDTH = 256;
    int YOU_GOT_A_CARD_COLUMN = 7;
    int YOU_GOT_A_CARD_ROW = 7;
    String FONT_PATH = "classpath:/font/bold/";
    String BOX_PATH = "classpath:/sprites/object/card/message/box.png";

    /**
     * @param glyph a character of the bold font
     * @return its {@value #CELL_SIZE} x {@value #CELL_SIZE} ARGB pixels
     */
    int[] glyphPixels(BoldFontGlyph glyph);

    /** @return the card's box: {@value #BOX_SIZE} x {@value #BOX_SIZE} ARGB pixels */
    int[] boxPixels();

    /**
     * @param message what has been written so far
     * @param card    the card collected, drawn in the box with "YOU GOT A CARD"
     * @return the view's ARGB pixels, row-major from the top
     */
    default int[] renderToPixels(
        final Message message,
        final LevelCompletionCardType card
    ) {
        final int[] pixels = new int[SCREEN_WIDTH * SCREEN_HEIGHT];
        if (message == Message.NONE) {
            return pixels;
        }
        writeText(pixels, Message.COURSE_CLEAR.getText(), COURSE_CLEAR_COLUMN, COURSE_CLEAR_ROW);
        if (message == Message.YOU_GOT_A_CARD) {
            final String text = Message.YOU_GOT_A_CARD.getText();
            writeText(pixels, text, YOU_GOT_A_CARD_COLUMN, YOU_GOT_A_CARD_ROW);
            // Blank cells close the gap up to the box
            writeText(pixels, " ".repeat(max(0, BOX_COLUMN - YOU_GOT_A_CARD_COLUMN - text.length())),
                YOU_GOT_A_CARD_COLUMN + text.length(), YOU_GOT_A_CARD_ROW);
            blit(pixels, boxPixels(), BOX_SIZE, BOX_SIZE, BOX_COLUMN * CELL_SIZE, BOX_ROW * CELL_SIZE);
            blit(pixels, cardPixels(card), CARD_SIZE, CARD_SIZE, CARD_X, CARD_Y);
        }
        return pixels;
    }

    /**
     * Writes a text one cell per character, left to right from the given cell.
     *
     * @throws IllegalArgumentException for a character the font does not have
     */
    default void writeText(
        final int[] pixels,
        final String text,
        final int column,
        final int row
    ) {
        for (int index = 0; index < text.length(); index++) {
            final int[] glyph = glyphPixels(glyphFor(text.charAt(index)));
            blit(pixels, glyph, CELL_SIZE, CELL_SIZE, (column + index) * CELL_SIZE, row * CELL_SIZE);
        }
    }

    /**
     * @param character a character of a text
     * @return the bold font's cell for it
     * @throws IllegalArgumentException when the font has no cell for it
     */
    default BoldFontGlyph glyphFor(final char character) {
        return Arrays.stream(BoldFontGlyph.values())
            .filter(glyph -> glyph.getCharacter() == character)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The bold font has no '" + character + "'"));
    }

    /** @return every character of the bold font, read from {@link #FONT_PATH} */
    default Map<BoldFontGlyph, int[]> loadGlyphs() {
        final Map<BoldFontGlyph, int[]> glyphs = new EnumMap<>(BoldFontGlyph.class);
        for (final BoldFontGlyph glyph : BoldFontGlyph.values()) {
            glyphs.put(glyph, readPixels(FONT_PATH + glyph.getFilename() + ".png", CELL_SIZE));
        }
        return glyphs;
    }

    /** @return the card's box, read from {@link #BOX_PATH} */
    default int[] loadBox() {
        return readPixels(BOX_PATH, BOX_SIZE);
    }

    /**
     * @param card a card
     * @return the status bar's picture of the card: {@value #CARD_SIZE} x {@value #CARD_SIZE} ARGB pixels
     */
    default int[] cardPixels(final LevelCompletionCardType card) {
        return readPixels(card.getStatusBarPath(), CARD_SIZE);
    }

    private int[] readPixels(
        final String path,
        final int size
    ) {
        try (final InputStream input = new DefaultResourceLoader().getResource(path).getInputStream()) {
            final BufferedImage image = ImageIO.read(input);
            final int[] argb = new int[size * size];
            image.getRGB(0, 0, size, size, argb, 0, size);
            return argb;
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    /** Copies the source's opaque pixels into the view at the given top-left, dropping anything outside it. */
    private void blit(
        final int[] pixels,
        final int[] source,
        final int width,
        final int height,
        final int posX,
        final int posY
    ) {
        for (int row = 0; row < height; row++) {
            final int destY = posY + row;
            if (destY < 0 || destY >= SCREEN_HEIGHT) {
                continue;
            }
            for (int col = 0; col < width; col++) {
                final int destX = posX + col;
                final int argb = source[row * width + col];
                if (destX >= 0 && destX < SCREEN_WIDTH && (argb >>> 24) > 0) {
                    pixels[destY * SCREEN_WIDTH + destX] = argb;
                }
            }
        }
    }
}
