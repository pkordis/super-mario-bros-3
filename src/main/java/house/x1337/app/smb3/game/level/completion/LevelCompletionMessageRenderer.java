package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.enumeration.BoldFontGlyph;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.Message;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.FireworksPicture;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.LivesBonusPicture;
import house.x1337.app.smb3.util.GameRenderer;
import org.springframework.core.io.DefaultResourceLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;

/**
 * Paints what the level completion shows ({@link LevelCompletionScreen}) into an ARGB picture of the level view, top
 * row first, transparent wherever nothing is drawn: the fireworks, the messages in the bold font, and the "nUP" over
 * them. Places are cells of the view; what the fanfare draws on the sky is placed from the level's top.
 *
 * <p>See {@code kb/level-completion.md}, "The messages, the new card on the status bar, the P-meter and the mute"
 * and "Three cards pay out lives", for where each piece goes.
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
    int DIGIT_HEIGHT = 16;
    int FANFARE_ROWS_DOWN = 15;
    int FIREWORKS_COLUMN = 8;
    int FIREWORKS_ROW = 4;
    int FIREWORKS_CELLS = 16;
    int SCREEN_HEIGHT = 192;
    int SCREEN_WIDTH = 256;
    int SPARKLES = 4;
    int UP_SIZE = 16;
    int YOU_GOT_A_CARD_COLUMN = 7;
    int YOU_GOT_A_CARD_ROW = 7;
    List<Integer> LIVES_BONUS_DIGITS = List.of(1, 2, 3, 5);
    String FONT_PATH = "classpath:/font/bold/";
    String BOX_PATH = "classpath:/sprites/object/card/message/box.png";
    String DIGIT_PATH = "classpath:/sprites/object/card/lives_bonus/digit_";
    String UP_PATH = "classpath:/sprites/object/card/lives_bonus/up.png";
    String BRIGHT_DOT_PATH = "classpath:/sprites/object/card/fireworks/bright/frame_";
    String DIM_DOT_PATH = "classpath:/sprites/object/card/fireworks/dim/frame_";

    int[] glyphPixels(BoldFontGlyph glyph);

    int[] boxPixels();

    int[] digitPixels(int lives);

    int[] upPixels();

    int[] dotPixels(boolean bright, int sparkle);

    default int[] renderToPixels(final LevelCompletionScreen screen) {
        final int[] pixels = new int[SCREEN_WIDTH * SCREEN_HEIGHT];
        if (screen.fireworks() != null) {
            drawTheFireworks(pixels, screen.fireworks(), screen.viewTop());
        }
        switch (screen.message()) {
            case COURSE_CLEAR ->
                writeText(pixels, Message.COURSE_CLEAR.getText(), COURSE_CLEAR_COLUMN, COURSE_CLEAR_ROW);
            case YOU_GOT_A_CARD -> {
                writeText(pixels, Message.COURSE_CLEAR.getText(), COURSE_CLEAR_COLUMN, COURSE_CLEAR_ROW);
                writeYouGotACard(pixels, screen.card(), 0);
            }
            case FANFARE_YOU_GOT_A_CARD ->
                writeYouGotACard(pixels, screen.card(), FANFARE_ROWS_DOWN * CELL_SIZE - screen.viewTop());
            default -> {
                // Nothing written
            }
        }
        if (screen.livesBonus() != null) {
            final LivesBonusPicture livesBonus = screen.livesBonus();
            blit(pixels, digitPixels(livesBonus.lives()), CELL_SIZE, DIGIT_HEIGHT, livesBonus.x(), livesBonus.y());
            blit(pixels, upPixels(), UP_SIZE, UP_SIZE, livesBonus.x() + CELL_SIZE, livesBonus.y());
        }
        return pixels;
    }

    /** Writes a text one cell per character, left to right from the given cell. */
    default void writeText(
        final int[] pixels,
        final String text,
        final int column,
        final int row
    ) {
        writeTextAt(pixels, text, column * CELL_SIZE, row * CELL_SIZE);
    }

    /** @throws IllegalArgumentException when the bold font has no cell for the character */
    default BoldFontGlyph glyphFor(final char character) {
        return Arrays.stream(BoldFontGlyph.values())
            .filter(glyph -> glyph.getCharacter() == character)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The bold font has no '" + character + "'"));
    }

    default FireworksShape fireworksShapeOf(final LevelCompletionCardType card) {
        return FireworksShape.valueOf(card.name());
    }

    default Map<BoldFontGlyph, int[]> loadGlyphs() {
        final Map<BoldFontGlyph, int[]> glyphs = new EnumMap<>(BoldFontGlyph.class);
        for (final BoldFontGlyph glyph : BoldFontGlyph.values()) {
            glyphs.put(glyph, readPixels(FONT_PATH + glyph.getFilename() + ".png", CELL_SIZE, CELL_SIZE));
        }
        return glyphs;
    }

    default int[] loadBox() {
        return readPixels(BOX_PATH, BOX_SIZE, BOX_SIZE);
    }

    default Map<Integer, int[]> loadDigits() {
        final Map<Integer, int[]> digits = new HashMap<>();
        for (final int lives : LIVES_BONUS_DIGITS) {
            digits.put(lives, readPixels(DIGIT_PATH + lives + ".png", CELL_SIZE, DIGIT_HEIGHT));
        }
        return digits;
    }

    default int[] loadUp() {
        return readPixels(UP_PATH, UP_SIZE, UP_SIZE);
    }

    default int[][] loadDots(final boolean bright) {
        final int[][] dots = new int[SPARKLES][];
        for (int sparkle = 0; sparkle < SPARKLES; sparkle++) {
            dots[sparkle] = readPixels((bright ? BRIGHT_DOT_PATH : DIM_DOT_PATH) + sparkle + ".png", CELL_SIZE,
                CELL_SIZE);
        }
        return dots;
    }

    default int[] cardPixels(final LevelCompletionCardType card) {
        return readPixels(card.getStatusBarPath(), CARD_SIZE, CARD_SIZE);
    }

    /** Writes "YOU GOT A CARD", its box and the card in it, {@code linesDown} lines below the ground's place. */
    private void writeYouGotACard(
        final int[] pixels,
        final LevelCompletionCardType card,
        final int linesDown
    ) {
        final String text = Message.YOU_GOT_A_CARD.getText();
        final int y = YOU_GOT_A_CARD_ROW * CELL_SIZE + linesDown;
        writeTextAt(pixels, text, YOU_GOT_A_CARD_COLUMN * CELL_SIZE, y);
        // Blank cells close the gap up to the box
        writeTextAt(pixels, " ".repeat(max(0, BOX_COLUMN - YOU_GOT_A_CARD_COLUMN - text.length())),
            (YOU_GOT_A_CARD_COLUMN + text.length()) * CELL_SIZE, y);
        blit(pixels, boxPixels(), BOX_SIZE, BOX_SIZE, BOX_COLUMN * CELL_SIZE, BOX_ROW * CELL_SIZE + linesDown);
        blit(pixels, cardPixels(card), CARD_SIZE, CARD_SIZE, CARD_X, CARD_Y + linesDown);
    }

    private void writeTextAt(
        final int[] pixels,
        final String text,
        final int x,
        final int y
    ) {
        for (int index = 0; index < text.length(); index++) {
            final int[] glyph = glyphPixels(glyphFor(text.charAt(index)));
            blit(pixels, glyph, CELL_SIZE, CELL_SIZE, x + index * CELL_SIZE, y);
        }
    }

    private void drawTheFireworks(
        final int[] pixels,
        final FireworksPicture fireworks,
        final int viewTop
    ) {
        final FireworksShape shape = fireworksShapeOf(fireworks.shape());
        for (int row = 0; row < FIREWORKS_CELLS; row++) {
            for (int column = 0; column < FIREWORKS_CELLS; column++) {
                if (!shape.hasDot(column, row) || !fireworks.fade().isSeen(column, row)) {
                    continue;
                }
                final int[] dot = dotPixels(fireworks.fade().isBright(column, row), fireworks.sparkle());
                blit(pixels, dot, CELL_SIZE, CELL_SIZE, (FIREWORKS_COLUMN + column) * CELL_SIZE,
                    (FIREWORKS_ROW + row) * CELL_SIZE - viewTop);
            }
        }
    }

    private int[] readPixels(
        final String path,
        final int width,
        final int height
    ) {
        try (final InputStream input = new DefaultResourceLoader().getResource(path).getInputStream()) {
            final BufferedImage image = ImageIO.read(input);
            final int[] argb = new int[width * height];
            image.getRGB(0, 0, width, height, argb, 0, width);
            return argb;
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

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
