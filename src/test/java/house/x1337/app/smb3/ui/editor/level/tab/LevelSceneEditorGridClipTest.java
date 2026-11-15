package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid.CellRange;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A repaint draws only the cells inside the area being painted, so a large scene repaints as fast as the part of it
 * on screen.
 */
class LevelSceneEditorGridClipTest {
    private static final int COLUMNS = 100;
    private static final int ROWS = 15;

    private final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
    private final Tile[][] tiles = new Tile[ROWS][COLUMNS];
    private LevelSceneEditorGrid grid;

    @BeforeEach
    void prepare() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                final Tile tile = mock(Tile.class);
                when(tile.getId()).thenReturn(row * COLUMNS + column + 1);
                tiles[row][column] = tile;
            }
        }
        final LevelSceneLayer layer = LevelSceneLayer.builder()
            .type(STATIC_ENVIRONMENT)
            .visible(true)
            .tiles(tiles)
            .build();
        when(tab.getLayers()).thenReturn(List.of(layer));
        when(tab.getColumns()).thenReturn(COLUMNS);
        when(tab.getRows()).thenReturn(ROWS);
        grid = new LevelSceneEditorGrid(tab, new SpawnPointSprite());
    }

    @Test
    @DisplayName("The cells to paint are those the clip touches, kept inside the scene")
    void cellsTouchedByTheClip() {
        // Execute & Verify
        assertThat(grid.visibleCells(new Rectangle(TILE_SIZE * 3 + 5, TILE_SIZE * 2, TILE_SIZE, TILE_SIZE)))
            .as("A clip straddling two columns")
            .isEqualTo(new CellRange(3, 4, 2, 2));
        assertThat(grid.visibleCells(null)).as("No clip").isEqualTo(new CellRange(0, COLUMNS - 1, 0, ROWS - 1));
        assertThat(grid.visibleCells(new Rectangle(TILE_SIZE * 98, TILE_SIZE * 14, TILE_SIZE * 10, TILE_SIZE * 10)))
            .as("A clip reaching past the scene's corner")
            .isEqualTo(new CellRange(98, COLUMNS - 1, 14, ROWS - 1));
    }

    @Test
    @DisplayName("Painting draws the tiles inside the clip and none of the others")
    void paintsOnlyInsideTheClip() {
        // Prepare
        final BufferedImage image = new BufferedImage(TILE_SIZE * 4, TILE_SIZE * 4, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setClip(new Rectangle(TILE_SIZE, TILE_SIZE, TILE_SIZE * 2, TILE_SIZE));

        // Execute
        grid.paintComponent(graphics);
        graphics.dispose();

        // Verify
        verify(tiles[1][1]).toImage();
        verify(tiles[1][2]).toImage();
        verify(tiles[0][0], never()).toImage();
        verify(tiles[1][3], never()).toImage();
        verify(tiles[2][1], never()).toImage();
        verify(tiles[ROWS - 1][COLUMNS - 1], never()).toImage();
    }
}
