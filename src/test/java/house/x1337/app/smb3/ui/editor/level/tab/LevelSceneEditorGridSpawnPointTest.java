package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.IntPredicate;

import static house.x1337.app.smb3.GameConstants.TILE_SCALE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The spawn point is drawn on the editor grid as a green 4 px border around the player's two cells with the standing
 * player inside, and the rendering starter as a blue 4 px border around its cell, both over the NPC layer.
 */
class LevelSceneEditorGridSpawnPointTest {
    private static final int GRID_ROWS = 4;
    private static final int GRID_COLUMNS = 4;
    private static final Coordinates SPAWN_POINT = of(1, 1);
    private static final Coordinates RENDERING_STARTER = of(3, 3);

    private final SpawnPointSprite sprite = new SpawnPointSprite();

    @Test
    @DisplayName("The green border surrounds exactly the spawn point's two cells")
    void greenBorderSurroundsBothCells() {
        // Prepare
        final Rectangle box = new Rectangle(TILE_SIZE, TILE_SIZE, TILE_SIZE, 2 * TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true);

        // Verify
        final Rectangle border = bounds(image, this::isGreen);
        assertThat(border).as("Green pixels").isNotNull();
        assertThat(slack(box).contains(border)).as("Every green pixel lies on the box: %s", border).isTrue();
        assertThat(isGreen(image.getRGB(box.x + 1, box.y + box.height / 2))).as("Left edge").isTrue();
        assertThat(isGreen(image.getRGB(box.x + box.width - 2, box.y + box.height / 2))).as("Right edge").isTrue();
        assertThat(isGreen(image.getRGB(box.x + box.width / 2, box.y + 1))).as("Top edge").isTrue();
        assertThat(isGreen(image.getRGB(box.x + box.width / 2, box.y + box.height - 2))).as("Bottom edge").isTrue();
    }

    @Test
    @DisplayName("The standing player fills the box at the tile scale, centred and standing on its bottom")
    void playerIsDrawnInsideTheBox() {
        // Prepare
        final BufferedImage source = sprite.getImage();
        final int width = source.getWidth() * TILE_SCALE;
        final int height = source.getHeight() * TILE_SCALE;
        final Rectangle box = new Rectangle(TILE_SIZE, TILE_SIZE, TILE_SIZE, 2 * TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true);

        // Verify
        final Rectangle player = bounds(image, this::isPlayer);
        assertThat(player).as("Player pixels").isNotNull();
        assertThat(box.contains(player)).as("The player stays inside the box: %s", player).isTrue();
        assertThat(player.x).as("Left edge, centred").isEqualTo(box.x + (box.width - width) / 2);
        assertThat(player.width).as("Width").isEqualTo(width);
        assertThat(player.y).as("Top of the head, standing on the box's bottom").isEqualTo(box.y + box.height - height);
    }

    @Test
    @DisplayName("The player is drawn pixel for pixel from the sprite, scaled with the tile")
    void playerIsDrawnFromTheSprite() {
        // Prepare
        final BufferedImage source = sprite.getImage();
        final int width = source.getWidth() * TILE_SCALE;
        final int left = TILE_SIZE + (TILE_SIZE - width) / 2;
        final int top = 3 * TILE_SIZE - source.getHeight() * TILE_SCALE;

        // Execute
        final BufferedImage image = paint(true);

        // Verify - the bottom row is left out, as the border is drawn over it
        for (int y = 0; y < source.getHeight() - 1; y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                final int expected = source.getRGB(x, y);
                final int drawn = image.getRGB(left + x * TILE_SCALE + 1, top + y * TILE_SCALE + 1);
                if ((expected >>> 24) == 255) {
                    assertThat(drawn).as("Pixel %d, %d", x, y).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    @DisplayName("The blue border surrounds exactly the rendering starter's cell")
    void blueBorderSurroundsTheRenderingStarter() {
        // Prepare
        final Rectangle cell = new Rectangle(3 * TILE_SIZE, 3 * TILE_SIZE, TILE_SIZE, TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true);

        // Verify
        final Rectangle border = bounds(image, this::isBlue);
        assertThat(border).as("Blue pixels").isNotNull();
        assertThat(slack(cell).contains(border)).as("Every blue pixel lies on the cell: %s", border).isTrue();
        assertThat(isBlue(image.getRGB(cell.x + cell.width / 2, cell.y + cell.height / 2)))
            .as("The middle of the cell is left clear")
            .isFalse();
    }

    @Test
    @DisplayName("Hiding the NPC layer hides the spawn point, its player and the rendering starter")
    void hiddenNpcLayerHidesThem() {
        // Execute
        final BufferedImage image = paint(false);

        // Verify
        assertThat(bounds(image, this::isGreen)).isNull();
        assertThat(bounds(image, this::isBlue)).isNull();
        assertThat(bounds(image, this::isPlayer)).isNull();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private BufferedImage paint(final boolean npcLayerVisible) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getRows()).thenReturn(GRID_ROWS);
        when(tab.getColumns()).thenReturn(GRID_COLUMNS);
        when(tab.getRenderingStarter()).thenReturn(RENDERING_STARTER);
        when(tab.getSpawnPoint()).thenReturn(SPAWN_POINT);
        when(tab.getLayers()).thenReturn(List.of(layer(AIR, true), layer(NON_PLAYABLE_CHARACTERS, npcLayerVisible)));
        when(tab.getPipePortals()).thenReturn(List.of());

        final BufferedImage image = new BufferedImage(GRID_COLUMNS * TILE_SIZE, GRID_ROWS * TILE_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        new LevelSceneEditorGrid(tab, sprite).paintComponent(graphics);
        graphics.dispose();
        return image;
    }

    private LevelSceneLayer layer(final LevelSceneLayerType type, final boolean visible) {
        return LevelSceneLayer.builder()
            .type(type)
            .visible(visible)
            .tiles(new Tile[GRID_ROWS][GRID_COLUMNS])
            .build();
    }

    private Rectangle slack(final Rectangle area) {
        return new Rectangle(area.x - 1, area.y - 1, area.width + 2, area.height + 2);
    }

    /** The bounding box of every pixel {@code matching}, or {@code null} when there is none. */
    private Rectangle bounds(final BufferedImage image, final IntPredicate matching) {
        Rectangle bounds = null;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (matching.test(image.getRGB(x, y))) {
                    final Rectangle pixel = new Rectangle(x, y, 1, 1);
                    bounds = bounds == null ? pixel : bounds.union(pixel);
                }
            }
        }
        return bounds;
    }

    private boolean isGreen(final int argb) {
        final Color colour = new Color(argb, true);
        return colour.getAlpha() > 128 && colour.getGreen() > 200 && colour.getRed() < 80 && colour.getBlue() < 80;
    }

    private boolean isBlue(final int argb) {
        final Color colour = new Color(argb, true);
        return colour.getAlpha() > 128 && colour.getBlue() > 200 && colour.getRed() < 80 && colour.getGreen() < 80;
    }

    /** Opaque, and neither a border nor a grid line. */
    private boolean isPlayer(final int argb) {
        final Color colour = new Color(argb, true);
        return colour.getAlpha() == 255 && !isGreen(argb) && !isBlue(argb);
    }
}
