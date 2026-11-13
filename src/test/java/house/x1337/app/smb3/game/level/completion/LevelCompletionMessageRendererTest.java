package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.enumeration.BoldFontGlyph;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.Message;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.FireworksPicture;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.LivesBonusPicture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.BOX_COLUMN;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.BOX_ROW;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.BOX_SIZE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.CARD_SIZE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.CARD_X;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.CARD_Y;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.CELL_SIZE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.DIGIT_HEIGHT;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.LIVES_BONUS_DIGITS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.SCREEN_HEIGHT;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.SCREEN_WIDTH;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.SPARKLES;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.UP_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the level completion shows over the 256 x 192 level view. The expected pictures under the test resources are
 * the two messages as the game shows them; the font must reproduce them pixel for pixel, at the same places, with
 * nothing anywhere else. The fanfare writes on the level's sky, so its pictures move up with the view's top: "YOU GOT
 * A CARD" with its box's top at line {@value #FANFARE_YOU_GOT_A_CARD_LEVEL_Y} of the level, and the fireworks over
 * cells 8 to 23 across and 4 to 19 down, from ({@value #FIREWORKS_X}, {@value #FIREWORKS_LEVEL_Y}).
 */
class LevelCompletionMessageRendererTest {
    private static final String EXPECTED_COURSE_CLEAR = "sprites/object/card/message/expected_course_clear.png";
    private static final String EXPECTED_YOU_GOT_A_CARD = "sprites/object/card/message/expected_you_got_a_card.png";
    private static final int COURSE_CLEAR_X = 72;
    private static final int COURSE_CLEAR_Y = 32;
    private static final int COURSE_CLEAR_WIDTH = 112;
    private static final int COURSE_CLEAR_HEIGHT = 8;
    private static final int YOU_GOT_A_CARD_X = 56;
    private static final int YOU_GOT_A_CARD_Y = 48;
    private static final int YOU_GOT_A_CARD_WIDTH = 152;
    private static final int YOU_GOT_A_CARD_HEIGHT = 32;
    private static final int FANFARE_YOU_GOT_A_CARD_LEVEL_Y = 168;
    private static final int FIREWORKS_X = 64;
    private static final int FIREWORKS_LEVEL_Y = 32;
    private static final int VIEW_TOP = 15;

    private int[] courseClear;
    private int[] youGotACard;
    private TestRenderer renderer;

    @BeforeEach
    void prepare() throws IOException {
        courseClear = readPixels(EXPECTED_COURSE_CLEAR, COURSE_CLEAR_WIDTH, COURSE_CLEAR_HEIGHT);
        youGotACard = readPixels(EXPECTED_YOU_GOT_A_CARD, YOU_GOT_A_CARD_WIDTH, YOU_GOT_A_CARD_HEIGHT);
        renderer = new TestRenderer();
    }

    @Test
    @DisplayName("Without anything to show the view is left alone: every pixel is transparent")
    void noMessageDrawsNothing() {
        // Execute
        final int[] pixels = renderer.renderToPixels(LevelCompletionScreen.NOTHING);

        // Verify
        assertThat(pixels).hasSize(SCREEN_WIDTH * SCREEN_HEIGHT).containsOnly(0);
        assertThat(LevelCompletionScreen.NOTHING.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("COURSE CLEAR ! is written as the game shows it at (72, 32), and nothing else is")
    void courseClearIsWrittenAlone() {
        // Execute
        final int[] pixels = renderer.renderToPixels(messageOnly(Message.COURSE_CLEAR, STAR));

        // Verify
        assertThat(region(pixels, COURSE_CLEAR_X, COURSE_CLEAR_Y, COURSE_CLEAR_WIDTH, COURSE_CLEAR_HEIGHT))
            .isEqualTo(courseClear);
        assertThat(opaqueCount(pixels)).as("Nothing else").isEqualTo(opaqueCount(courseClear));
    }

    @Test
    @DisplayName("YOU GOT A CARD and its box are written as the game shows them, with the card in the box and "
        + "COURSE CLEAR ! still above")
    void youGotACardShowsBothAndTheCard() throws IOException {
        // Execute & Verify
        for (final LevelCompletionCardType card : LevelCompletionCardType.values()) {
            final int[] pixels = renderer.renderToPixels(messageOnly(Message.YOU_GOT_A_CARD, card));
            assertThat(region(pixels, COURSE_CLEAR_X, COURSE_CLEAR_Y, COURSE_CLEAR_WIDTH, COURSE_CLEAR_HEIGHT))
                .as("COURSE CLEAR ! stays").isEqualTo(courseClear);
            assertYouGotACardAt(pixels, YOU_GOT_A_CARD_Y, card);
            assertThat(opaqueCount(pixels)).as("Nothing else, %s", card)
                .isEqualTo(opaqueCount(courseClear) + opaqueCount(youGotACard));
        }
    }

    @Test
    @DisplayName("The fanfare's YOU GOT A CARD is the same picture alone, 15 rows lower on the level, so it moves up "
        + "with the view's top")
    void theFanfaresYouGotACardIsInTheSky() throws IOException {
        // Execute & Verify
        for (final int viewTop : new int[] {VIEW_TOP, 40}) {
            final int[] pixels = renderer.renderToPixels(
                new LevelCompletionScreen(Message.FANFARE_YOU_GOT_A_CARD, FLOWER, viewTop, null, null)
            );
            assertYouGotACardAt(pixels, FANFARE_YOU_GOT_A_CARD_LEVEL_Y - viewTop, FLOWER);
            assertThat(opaqueCount(pixels)).as("No COURSE CLEAR !, view top %d", viewTop)
                .isEqualTo(opaqueCount(youGotACard));
        }
    }

    @Test
    @DisplayName("Fully faded in, the fireworks have a bright dot of the tick's sparkle in each of their cells, and "
        + "nothing anywhere else")
    void theFireworksAreTheirDots() {
        // Execute & Verify
        for (final LevelCompletionCardType card : LevelCompletionCardType.values()) {
            for (int sparkle = 0; sparkle < SPARKLES; sparkle++) {
                final int[] pixels = renderer.renderToPixels(fireworks(card, FireworksFade.ALL_BRIGHT, sparkle));
                final FireworksShape shape = renderer.fireworksShapeOf(card);
                long dots = 0;
                for (int row = 0; row < 16; row++) {
                    for (int column = 0; column < 16; column++) {
                        final int[] cell = region(pixels, FIREWORKS_X + column * CELL_SIZE,
                            FIREWORKS_LEVEL_Y + row * CELL_SIZE - VIEW_TOP, CELL_SIZE, CELL_SIZE);
                        if (shape.hasDot(column, row)) {
                            assertThat(cell).as("%s (%d, %d)", card, column, row)
                                .isEqualTo(renderer.dotPixels(true, sparkle));
                            dots++;
                        } else {
                            assertThat(opaqueCount(cell)).as("%s (%d, %d)", card, column, row).isZero();
                        }
                    }
                }
                assertThat(opaqueCount(pixels)).as("Nothing else, %s", card)
                    .isEqualTo(dots * opaqueCount(renderer.dotPixels(true, sparkle)));
            }
        }
    }

    @Test
    @DisplayName("As they fade in, the fireworks' dots show dim or bright only in the blocks the fade has reached")
    void theFireworksFadeInFromTheMiddle() {
        // Execute
        final int[] middle = renderer.renderToPixels(fireworks(MUSHROOM, FireworksFade.MIDDLE, 0));
        final int[] middleBright = renderer.renderToPixels(fireworks(MUSHROOM, FireworksFade.MIDDLE_BRIGHT, 0));

        // Verify - the middle blocks are cells 6 to 9 across and down; the mushroom has dots at (6, 6), (5, 6), (2, 6)
        assertThat(dotAt(middle, 6, 6)).as("Middle, dim").isEqualTo(renderer.dotPixels(false, 0));
        assertThat(opaqueCount(dotAt(middle, 5, 6))).as("Middle, not reached yet").isZero();
        assertThat(dotAt(middleBright, 6, 6)).as("Middle bright").isEqualTo(renderer.dotPixels(true, 0));
        assertThat(dotAt(middleBright, 5, 6)).as("Middle bright, dim around it")
            .isEqualTo(renderer.dotPixels(false, 0));
        assertThat(opaqueCount(dotAt(middleBright, 2, 6))).as("Middle bright, not reached yet").isZero();
    }

    @Test
    @DisplayName("The lives bonus draws its big digit, then UP beside it, over the rest, cut off at the view's bottom")
    void theLivesBonusIsItsDigitAndUp() {
        // Execute & Verify
        for (final int lives : LIVES_BONUS_DIGITS) {
            final int[] pixels = renderer.renderToPixels(livesBonus(lives, 100));
            assertThat(region(pixels, 124, 100, CELL_SIZE, DIGIT_HEIGHT)).as("%d", lives)
                .isEqualTo(renderer.digitPixels(lives));
            assertThat(region(pixels, 132, 100, UP_SIZE, UP_SIZE)).as("UP").isEqualTo(renderer.upPixels());
            assertThat(opaqueCount(pixels)).as("Nothing else")
                .isEqualTo(opaqueCount(renderer.digitPixels(lives)) + opaqueCount(renderer.upPixels()));
        }
        final int[] cutOff = renderer.renderToPixels(livesBonus(1, SCREEN_HEIGHT - 2));
        assertThat(region(cutOff, 124, SCREEN_HEIGHT - 2, CELL_SIZE, 2))
            .isEqualTo(Arrays.copyOf(renderer.digitPixels(1), 2 * CELL_SIZE));
        assertThat(renderer.renderToPixels(livesBonus(1, SCREEN_HEIGHT))).as("Below the view").containsOnly(0);
    }

    @Test
    @DisplayName("The big digits, UP and the dots are pictures of their own: 1, 2, 3 and 5 differ, and the sparkle "
        + "goes small dot, big dot, square, big dot, dim and bright")
    void theBonusPicturesAreTheirOwn() {
        // Execute & Verify
        assertThat(LIVES_BONUS_DIGITS.stream().map(lives -> Arrays.toString(renderer.digitPixels(lives))).distinct())
            .hasSize(LIVES_BONUS_DIGITS.size());
        for (final boolean bright : new boolean[] {true, false}) {
            assertThat(IntStream.range(0, SPARKLES)
                .mapToObj(sparkle -> Arrays.toString(renderer.dotPixels(bright, sparkle))).distinct())
                .as(bright ? "Bright" : "Dim").hasSize(SPARKLES - 1);
            assertThat(renderer.dotPixels(bright, 3)).as("The big dot again").isEqualTo(renderer.dotPixels(bright, 1));
            assertThat(opaqueCount(renderer.dotPixels(bright, 0)))
                .as("The small dot is the smallest")
                .isLessThan(opaqueCount(renderer.dotPixels(bright, 1)))
                .isLessThan(opaqueCount(renderer.dotPixels(bright, 2)));
        }
        assertThat(renderer.dotPixels(true, 0)).isNotEqualTo(renderer.dotPixels(false, 0));
        assertThat(opaqueCount(renderer.upPixels())).isPositive();
    }

    @Test
    @DisplayName("Any text of the font's characters is written one cell per character, left to right")
    void anyTextOfTheFontIsWritten() {
        // Prepare
        final int[] pixels = new int[SCREEN_WIDTH * SCREEN_HEIGHT];

        // Execute
        renderer.writeText(pixels, "GO !", 2, 3);

        // Verify
        final BoldFontGlyph[] expected = {
            BoldFontGlyph.G,
            BoldFontGlyph.O,
            BoldFontGlyph.SPACE,
            BoldFontGlyph.EXCLAMATION
        };
        long written = 0;
        for (int index = 0; index < expected.length; index++) {
            final int[] glyph = renderer.glyphPixels(expected[index]);
            assertThat(region(pixels, (2 + index) * CELL_SIZE, 3 * CELL_SIZE, CELL_SIZE, CELL_SIZE))
                .as("Cell %d", index).isEqualTo(glyph);
            written += opaqueCount(glyph);
        }
        assertThat(opaqueCount(pixels)).as("Nothing else").isEqualTo(written);
    }

    @Test
    @DisplayName("A character the font does not have is refused, naming it")
    void aCharacterOutsideTheFontIsRefused() {
        // Execute & Verify
        assertThatThrownBy(() -> renderer.glyphFor('W'))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'W'");
    }

    @Test
    @DisplayName("Every character of the bold font is an 8 x 8 picture of its own, opaque only where it is drawn")
    void everyCharacterIsAPictureOfItsOwn() {
        // Execute & Verify
        for (final BoldFontGlyph glyph : BoldFontGlyph.values()) {
            final int[] pixels = renderer.glyphPixels(glyph);
            assertThat(pixels).as("%s", glyph).hasSize(CELL_SIZE * CELL_SIZE);
            assertThat(Arrays.stream(pixels).allMatch(argb -> argb >>> 24 == 0 || argb >>> 24 == 255))
                .as("%s is either drawn or transparent, never see-through", glyph)
                .isTrue();
            if (glyph == BoldFontGlyph.SPACE) {
                assertThat(opaqueCount(pixels)).as("The space is blank").isZero();
            } else {
                assertThat(opaqueCount(pixels)).as("%s is drawn", glyph).isPositive();
            }
        }
        assertThat(Arrays.stream(BoldFontGlyph.values())
            .map(glyph -> Arrays.toString(renderer.glyphPixels(glyph)))
            .distinct()
            .count())
            .as("One picture per character")
            .isEqualTo(BoldFontGlyph.values().length);
    }

    @Test
    @DisplayName("The card's box is one 32 x 32 picture, opaque all over the place the card goes")
    void theBoxIsOnePicture() {
        // Prepare
        final int cardLeft = CARD_X - BOX_COLUMN * CELL_SIZE;
        final int cardTop = CARD_Y - BOX_ROW * CELL_SIZE;

        // Execute & Verify
        assertThat(renderer.boxPixels()).hasSize(BOX_SIZE * BOX_SIZE);
        for (int row = cardTop; row < cardTop + CARD_SIZE; row++) {
            for (int col = cardLeft; col < cardLeft + CARD_SIZE; col++) {
                assertThat(renderer.boxPixels()[row * BOX_SIZE + col] >>> 24).as("(%d, %d)", col, row)
                    .isEqualTo(255);
            }
        }
    }

    private LevelCompletionScreen messageOnly(
        final Message message,
        final LevelCompletionCardType card
    ) {
        return new LevelCompletionScreen(message, card, 0, null, null);
    }

    private LevelCompletionScreen fireworks(
        final LevelCompletionCardType card,
        final FireworksFade fade,
        final int sparkle
    ) {
        return new LevelCompletionScreen(Message.NONE, card, VIEW_TOP, new FireworksPicture(card, fade, sparkle), null);
    }

    private LevelCompletionScreen livesBonus(
        final int lives,
        final int y
    ) {
        return new LevelCompletionScreen(Message.NONE, null, 0, null, new LivesBonusPicture(lives, 124, y));
    }

    private int[] dotAt(
        final int[] pixels,
        final int column,
        final int row
    ) {
        return region(pixels, FIREWORKS_X + column * CELL_SIZE, FIREWORKS_LEVEL_Y + row * CELL_SIZE - VIEW_TOP,
            CELL_SIZE, CELL_SIZE);
    }

    /** "YOU GOT A CARD" and its box with their top at {@code y}, and the card in the box. */
    private void assertYouGotACardAt(
        final int[] pixels,
        final int y,
        final LevelCompletionCardType card
    ) throws IOException {
        final int cardY = y + CARD_Y - YOU_GOT_A_CARD_Y;
        assertThat(region(pixels, CARD_X, cardY, CARD_SIZE, CARD_SIZE)).as("%s in the box", card)
            .isEqualTo(readPixels(card.getStatusBarPath().replace("classpath:/", ""), CARD_SIZE, CARD_SIZE));
        final int[] written = region(pixels, YOU_GOT_A_CARD_X, y, YOU_GOT_A_CARD_WIDTH, YOU_GOT_A_CARD_HEIGHT);
        for (int index = 0; index < written.length; index++) {
            final int x = YOU_GOT_A_CARD_X + index % YOU_GOT_A_CARD_WIDTH;
            final int line = y + index / YOU_GOT_A_CARD_WIDTH;
            final boolean inTheCard = x >= CARD_X && x < CARD_X + CARD_SIZE
                && line >= cardY && line < cardY + CARD_SIZE;
            if (!inTheCard) {
                assertThat(written[index]).as("(%d, %d), %s", x, line, card).isEqualTo(youGotACard[index]);
            }
        }
    }

    private int[] region(final int[] pixels, final int x, final int y, final int width, final int height) {
        final int[] region = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(pixels, (y + row) * SCREEN_WIDTH + x, region, row * width, width);
        }
        return region;
    }

    private long opaqueCount(final int[] pixels) {
        return Arrays.stream(pixels).filter(argb -> argb >>> 24 > 0).count();
    }

    private int[] readPixels(final String resource, final int width, final int height) throws IOException {
        try (InputStream input = LevelCompletionMessageRendererTest.class.getClassLoader()
            .getResourceAsStream(resource)) {
            assertThat(input).as("%s exists on the classpath", resource).isNotNull();
            final BufferedImage image = ImageIO.read(input);
            assertThat(image.getWidth()).as("%s width", resource).isEqualTo(width);
            assertThat(image.getHeight()).as("%s height", resource).isEqualTo(height);
            return image.getRGB(0, 0, width, height, null, 0, width);
        }
    }

    /** The renderer with every picture loaded once, as the overlay loads them. */
    private static final class TestRenderer implements LevelCompletionMessageRenderer {
        private final Map<BoldFontGlyph, int[]> glyphs = new EnumMap<>(BoldFontGlyph.class);
        private final Map<Integer, int[]> digits = new HashMap<>();
        private final int[] box;
        private final int[] up;
        private final int[][] brightDots;
        private final int[][] dimDots;

        TestRenderer() {
            glyphs.putAll(loadGlyphs());
            digits.putAll(loadDigits());
            box = loadBox();
            up = loadUp();
            brightDots = loadDots(true);
            dimDots = loadDots(false);
        }

        @Override
        public int[] glyphPixels(final BoldFontGlyph glyph) {
            return glyphs.get(glyph);
        }

        @Override
        public int[] boxPixels() {
            return box;
        }

        @Override
        public int[] digitPixels(final int lives) {
            return digits.get(lives);
        }

        @Override
        public int[] upPixels() {
            return up;
        }

        @Override
        public int[] dotPixels(
            final boolean bright,
            final int sparkle
        ) {
            return (bright ? brightDots : dimDots)[sparkle];
        }
    }
}
