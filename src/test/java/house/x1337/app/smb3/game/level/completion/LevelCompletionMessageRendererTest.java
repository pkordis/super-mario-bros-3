package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.enumeration.BoldFontGlyph;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

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
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.SCREEN_HEIGHT;
import static house.x1337.app.smb3.game.level.completion.LevelCompletionMessageRenderer.SCREEN_WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The level completion's messages written with the message font over the 256 x 192 level view. The expected
 * pictures under the test resources are the two messages as the game shows them; the font must reproduce them pixel
 * for pixel, at the same places, with nothing anywhere else.
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

    private int[] courseClear;
    private int[] youGotACard;
    private TestRenderer renderer;

    @BeforeEach
    void prepare() throws IOException {
        courseClear = readPixels(EXPECTED_COURSE_CLEAR, COURSE_CLEAR_WIDTH, COURSE_CLEAR_HEIGHT);
        youGotACard = readPixels(EXPECTED_YOU_GOT_A_CARD, YOU_GOT_A_CARD_WIDTH, YOU_GOT_A_CARD_HEIGHT);
        renderer = new TestRenderer(new EnumMap<>(BoldFontGlyph.class), new int[BOX_SIZE * BOX_SIZE]);
        renderer.glyphs().putAll(renderer.loadGlyphs());
        System.arraycopy(renderer.loadBox(), 0, renderer.box(), 0, BOX_SIZE * BOX_SIZE);
    }

    @Test
    @DisplayName("Without a message the view is left alone: every pixel is transparent")
    void noMessageDrawsNothing() {
        // Execute
        final int[] pixels = renderer.renderToPixels(Message.NONE, null);

        // Verify
        assertThat(pixels).hasSize(SCREEN_WIDTH * SCREEN_HEIGHT).containsOnly(0);
    }

    @Test
    @DisplayName("COURSE CLEAR ! is written as the game shows it at (72, 32), and nothing else is")
    void courseClearIsWrittenAlone() {
        // Execute
        final int[] pixels = renderer.renderToPixels(Message.COURSE_CLEAR, STAR);

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
        for (final LevelCompletionCardType card : new LevelCompletionCardType[] {MUSHROOM, FLOWER, STAR}) {
            final int[] pixels = renderer.renderToPixels(Message.YOU_GOT_A_CARD, card);
            assertThat(region(pixels, COURSE_CLEAR_X, COURSE_CLEAR_Y, COURSE_CLEAR_WIDTH, COURSE_CLEAR_HEIGHT))
                .as("COURSE CLEAR ! stays").isEqualTo(courseClear);
            assertThat(region(pixels, CARD_X, CARD_Y, CARD_SIZE, CARD_SIZE)).as("%s in the box", card)
                .isEqualTo(readPixels(card.getStatusBarPath().replace("classpath:/", ""), CARD_SIZE, CARD_SIZE));
            final int[] written = region(pixels, YOU_GOT_A_CARD_X, YOU_GOT_A_CARD_Y, YOU_GOT_A_CARD_WIDTH,
                YOU_GOT_A_CARD_HEIGHT);
            for (int index = 0; index < written.length; index++) {
                final int x = YOU_GOT_A_CARD_X + index % YOU_GOT_A_CARD_WIDTH;
                final int y = YOU_GOT_A_CARD_Y + index / YOU_GOT_A_CARD_WIDTH;
                final boolean inTheCard = x >= CARD_X && x < CARD_X + CARD_SIZE
                    && y >= CARD_Y && y < CARD_Y + CARD_SIZE;
                if (!inTheCard) {
                    assertThat(written[index]).as("(%d, %d), %s", x, y, card).isEqualTo(youGotACard[index]);
                }
            }
            assertThat(opaqueCount(pixels)).as("Nothing else, %s", card)
                .isEqualTo(opaqueCount(courseClear) + opaqueCount(youGotACard));
        }
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
        assertThat(renderer.box()).hasSize(BOX_SIZE * BOX_SIZE);
        for (int row = cardTop; row < cardTop + CARD_SIZE; row++) {
            for (int col = cardLeft; col < cardLeft + CARD_SIZE; col++) {
                assertThat(renderer.box()[row * BOX_SIZE + col] >>> 24).as("(%d, %d)", col, row).isEqualTo(255);
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

    private record TestRenderer(
        Map<BoldFontGlyph, int[]> glyphs,
        int[] box
    ) implements LevelCompletionMessageRenderer {
        @Override
        public int[] boxPixels() {
            return box;
        }

        @Override
        public int[] glyphPixels(final BoldFontGlyph glyph) {
            return glyphs.get(glyph);
        }
    }
}
