package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The level completion card is drawn on the editor grid as a yellow 4 px border around its one cell, over the NPC
 * layer.
 */
class LevelSceneEditorGridLevelCompletionCardTest {
    private static final int GRID_ROWS = 3;
    private static final int GRID_COLUMNS = 4;
    private static final int CARD_ROW = 1;
    private static final int CARD_COLUMN = 2;

    @Test
    @DisplayName("The yellow border surrounds exactly the card's cell")
    void borderSurroundsExactlyTheCardsCell() {
        // Prepare
        final Rectangle cell = new Rectangle(CARD_COLUMN * TILE_SIZE, CARD_ROW * TILE_SIZE, TILE_SIZE, TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true, true);

        // Verify
        final Rectangle border = yellowBounds(image);
        final Rectangle slack = new Rectangle(cell.x - 1, cell.y - 1, cell.width + 2, cell.height + 2);
        assertThat(border).as("Yellow pixels").isNotNull();
        assertThat(slack.contains(border))
            .as("Every yellow pixel lies on the card's cell, give or take a pixel of antialiasing: %s", border)
            .isTrue();
        assertThat(isYellow(image.getRGB(cell.x + 3, cell.y + cell.height / 2))).as("Left edge").isTrue();
        assertThat(isYellow(image.getRGB(cell.x + cell.width - 3, cell.y + cell.height / 2))).as("Right edge").isTrue();
        assertThat(isYellow(image.getRGB(cell.x + cell.width / 2, cell.y + 3))).as("Top edge").isTrue();
        assertThat(isYellow(image.getRGB(cell.x + cell.width / 2, cell.y + cell.height - 3)))
            .as("Bottom edge")
            .isTrue();
        assertThat(isYellow(image.getRGB(cell.x + cell.width / 2, cell.y + cell.height / 2)))
            .as("The middle of the cell is left clear")
            .isFalse();
    }

    @Test
    @DisplayName("Hiding the NPC layer hides the card")
    void hiddenNpcLayerHidesTheCard() {
        // Execute
        final BufferedImage image = paint(false, true);

        // Verify
        assertThat(yellowBounds(image)).isNull();
    }

    @Test
    @DisplayName("A scene without a card draws no yellow border")
    void noCardDrawsNothing() {
        // Execute
        final BufferedImage image = paint(true, false);

        // Verify
        assertThat(yellowBounds(image)).isNull();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static BufferedImage paint(final boolean npcLayerVisible, final boolean hasCard) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getRows()).thenReturn(GRID_ROWS);
        when(tab.getColumns()).thenReturn(GRID_COLUMNS);
        when(tab.getRenderingStarter()).thenReturn(null);
        when(tab.getSpawnPoint()).thenReturn(null);
        when(tab.getLayers()).thenReturn(List.of(layer(AIR, true), layer(NON_PLAYABLE_CHARACTERS, npcLayerVisible)));
        when(tab.getPipePortals()).thenReturn(List.of());
        when(tab.hasLevelCompletionCard()).thenReturn(hasCard);
        when(tab.getLevelCompletionCard()).thenReturn(hasCard ? of(CARD_COLUMN, CARD_ROW) : null);

        final BufferedImage image = new BufferedImage(GRID_COLUMNS * TILE_SIZE, GRID_ROWS * TILE_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        new LevelSceneEditorGrid(tab).paintComponent(graphics);
        graphics.dispose();
        return image;
    }

    private static LevelSceneLayer layer(final LevelSceneLayerType type, final boolean visible) {
        return LevelSceneLayer.builder()
            .type(type)
            .visible(visible)
            .tiles(new Tile[GRID_ROWS][GRID_COLUMNS])
            .build();
    }

    /** The bounding box of every yellow pixel in the image, or {@code null} when there is none. */
    private static Rectangle yellowBounds(final BufferedImage image) {
        Rectangle bounds = null;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isYellow(image.getRGB(x, y))) {
                    final Rectangle pixel = new Rectangle(x, y, 1, 1);
                    bounds = bounds == null ? pixel : bounds.union(pixel);
                }
            }
        }
        return bounds;
    }

    private static boolean isYellow(final int argb) {
        final Color colour = new Color(argb, true);
        return colour.getAlpha() > 128 && colour.getRed() > 200 && colour.getGreen() > 200 && colour.getBlue() < 80;
    }
}
