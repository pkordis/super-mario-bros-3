package house.x1337.app.smb3.game.hud;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.model.ImageResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.enumeration.PlayerIdentityType.MARIO;
import static house.x1337.app.smb3.game.hud.HeadsUpDisplayFontRenderer.CARD_SIZE;
import static house.x1337.app.smb3.game.hud.HeadsUpDisplayFontRenderer.HUD_HEIGHT;
import static house.x1337.app.smb3.game.hud.HeadsUpDisplayFontRenderer.HUD_WIDTH;
import static house.x1337.app.smb3.game.hud.HeadsUpDisplayRenderer.CARD_Y;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * The status bar's three card boxes: a held card is drawn over its box, and an empty box stays as
 * {@code hud_base.png} has it.
 */
class HeadsUpDisplayCardRenderingTest {
    private static final int[] SLOT_X = {168, 192, 216};

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private int[] basePixels;
    private TestRenderer renderer;

    @BeforeEach
    void prepare() throws IOException {
        // ImageResource and PlayerIdentity are prototype beans built through getBean; a plain instance stands in.
        staticBeanFactory = mockStatic(
            StaticBeanFactory.class,
            invocation -> invocation.<Class<?>>getArgument(0).getDeclaredConstructor().newInstance()
        );
        basePixels = readPixels("hud/hud_base.png", HUD_WIDTH, HUD_HEIGHT);
        renderer = new TestRenderer(ImageResource.fromData(basePixels.clone(), HUD_WIDTH, HUD_HEIGHT));
        renderer.initFontRenderer();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Without cards every box looks as the base image has it")
    void emptySlotsKeepTheBaseImage() {
        // Prepare
        final PlayerData playerData = player();

        // Execute
        final int[] pixels = renderer.renderToPixels(playerData);

        // Verify
        for (int slot = 0; slot < SLOT_X.length; slot++) {
            assertThat(slot(pixels, slot)).as("Box %d", slot + 1).isEqualTo(slot(basePixels, slot));
        }
    }

    @Test
    @DisplayName("Each held card is drawn in its own box, left to right, and the boxes after them stay empty")
    void heldCardsAreDrawnLeftToRight() throws IOException {
        // Prepare
        final PlayerData playerData = player();
        playerData.addCard(STAR);
        playerData.addCard(MUSHROOM);

        // Execute
        final int[] pixels = renderer.renderToPixels(playerData);

        // Verify
        assertThat(slot(pixels, 0)).as("Box 1 holds the star").isEqualTo(card(STAR));
        assertThat(slot(pixels, 1)).as("Box 2 holds the mushroom").isEqualTo(card(MUSHROOM));
        assertThat(slot(pixels, 2)).as("Box 3 is empty").isEqualTo(slot(basePixels, 2));
    }

    @Test
    @DisplayName("Three cards fill all three boxes and leave the rest of the status bar alone")
    void threeCardsFillAllThreeBoxes() throws IOException {
        // Prepare
        final PlayerData playerData = player();
        final int[] withoutCards = renderer.renderToPixels(playerData);
        playerData.addCard(FLOWER);
        playerData.addCard(FLOWER);
        playerData.addCard(STAR);

        // Execute
        final int[] pixels = renderer.renderToPixels(playerData);

        // Verify
        assertThat(slot(pixels, 0)).as("Box 1").isEqualTo(card(FLOWER));
        assertThat(slot(pixels, 1)).as("Box 2").isEqualTo(card(FLOWER));
        assertThat(slot(pixels, 2)).as("Box 3").isEqualTo(card(STAR));
        for (int slot = 0; slot < SLOT_X.length; slot++) {
            erase(pixels, slot);
            erase(withoutCards, slot);
        }
        assertThat(pixels).as("Everything outside the boxes").isEqualTo(withoutCards);
    }

    @Test
    @DisplayName("Every status-bar card is a picture distinct from the empty box")
    void everyCardIsDrawn() throws IOException {
        // Prepare
        final int[] emptyBox = slot(basePixels, 0);

        // Execute & Verify
        for (final LevelCompletionCardType card : LevelCompletionCardType.values()) {
            assertThat(renderer.pixels(card)).as("%s as loaded by the renderer", card).isEqualTo(card(card));
            assertThat(card(card)).as("%s differs from the empty box", card).isNotEqualTo(emptyBox);
        }
    }

    @Test
    @DisplayName("A hidden slot is left empty, and only it: the cards before and after it are still drawn")
    void aHiddenSlotIsLeftEmpty() throws IOException {
        // Prepare
        final TestRenderer hiding = new TestRenderer(renderer.baseImage(), Set.of(1));
        final PlayerData playerData = player();
        playerData.addCard(STAR);
        playerData.addCard(MUSHROOM);
        playerData.addCard(FLOWER);

        // Execute
        final int[] pixels = hiding.renderToPixels(playerData);

        // Verify
        assertThat(slot(pixels, 0)).as("Box 1 still holds the star").isEqualTo(card(STAR));
        assertThat(slot(pixels, 1)).as("Box 2 is empty").isEqualTo(slot(basePixels, 1));
        assertThat(slot(pixels, 2)).as("Box 3 still holds the flower").isEqualTo(card(FLOWER));
    }

    @Test
    @DisplayName("A hidden third slot leaves the first two drawn, and a hidden empty slot stays empty")
    void aHiddenThirdCardLeavesTheFirstTwo() throws IOException {
        // Prepare
        final TestRenderer hiding = new TestRenderer(renderer.baseImage(), Set.of(2));
        final PlayerData playerData = player();
        playerData.addCard(FLOWER);
        playerData.addCard(STAR);
        playerData.addCard(MUSHROOM);
        final PlayerData oneCard = player();
        oneCard.addCard(STAR);

        // Execute
        final int[] pixels = hiding.renderToPixels(playerData);
        final int[] withOneCard = hiding.renderToPixels(oneCard);

        // Verify
        assertThat(slot(pixels, 0)).as("Box 1").isEqualTo(card(FLOWER));
        assertThat(slot(pixels, 1)).as("Box 2").isEqualTo(card(STAR));
        assertThat(slot(pixels, 2)).as("Box 3 is empty").isEqualTo(slot(basePixels, 2));
        assertThat(slot(withOneCard, 0)).as("One card, box 1").isEqualTo(card(STAR));
        assertThat(slot(withOneCard, 2)).as("One card, box 3").isEqualTo(slot(basePixels, 2));
    }

    private static PlayerData player() {
        final PlayerData playerData = new PlayerData();
        playerData.setIdentity(MARIO.identity());
        return playerData;
    }

    private static int[] slot(final int[] pixels, final int slot) {
        final int[] region = new int[CARD_SIZE * CARD_SIZE];
        for (int row = 0; row < CARD_SIZE; row++) {
            System.arraycopy(pixels, (CARD_Y + row) * HUD_WIDTH + SLOT_X[slot], region, row * CARD_SIZE, CARD_SIZE);
        }
        return region;
    }

    private static void erase(final int[] pixels, final int slot) {
        for (int row = 0; row < CARD_SIZE; row++) {
            final int start = (CARD_Y + row) * HUD_WIDTH + SLOT_X[slot];
            Arrays.fill(pixels, start, start + CARD_SIZE, 0);
        }
    }

    private static int[] card(final LevelCompletionCardType card) throws IOException {
        return readPixels(card.getStatusBarPath().replace("classpath:/", ""), CARD_SIZE, CARD_SIZE);
    }

    private static int[] readPixels(final String resource, final int width, final int height) throws IOException {
        try (InputStream input = HeadsUpDisplayCardRenderingTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("%s exists on the classpath", resource).isNotNull();
            final BufferedImage image = ImageIO.read(input);
            assertThat(image.getWidth()).as("%s width", resource).isEqualTo(width);
            assertThat(image.getHeight()).as("%s height", resource).isEqualTo(height);
            return image.getRGB(0, 0, width, height, null, 0, width);
        }
    }

    private record TestRenderer(ImageResource baseImage, Set<Integer> hiddenSlots) implements HeadsUpDisplayRenderer {
        TestRenderer(final ImageResource baseImage) {
            this(baseImage, Set.of());
        }

        @Override
        public ImageResource getBaseImage() {
            return baseImage;
        }

        @Override
        public boolean isCardSlotHidden(final int slot) {
            return hiddenSlots.contains(slot);
        }

        @Override
        public GameEngine getGameEngine() {
            return null;
        }
    }
}
