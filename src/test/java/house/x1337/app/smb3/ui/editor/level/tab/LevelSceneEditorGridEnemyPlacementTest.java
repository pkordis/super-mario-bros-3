package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyTilesAssembler;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static javax.swing.JComponent.WHEN_FOCUSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Placed enemies are drawn on the grid at their pixel position, not on the tile grid, and the selected one is
 * outlined and nudged by the arrow keys. The scene is 6 columns by 5 rows; the enemy is one red tile.
 */
class LevelSceneEditorGridEnemyPlacementTest {
    private static final int ROWS = 5;
    private static final int COLUMNS = 6;
    private static final int RED = 0xFFFF0000;

    private final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
    private final List<EnemyPlacement> placements = new ArrayList<>();
    private final EnemyStamp stamp = EnemyStamp
        .builder()
        .id("goomba")
        .enemyType(GOOMBA_NORMAL)
        .tiles(new Tile[][] {{redTile()}})
        .renderingStarter(of(0, 0))
        .build();
    private LevelSceneEditorGrid grid;

    @BeforeEach
    void prepare() {
        final LevelSceneLayer npc = LevelSceneLayer.builder()
            .type(NON_PLAYABLE_CHARACTERS)
            .visible(true)
            .tiles(empty())
            .enemyPlacements(placements)
            .build();
        when(tab.getLayers()).thenReturn(List.of(npc));
        when(tab.getLevelSceneDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(tab.getPipePortals()).thenReturn(List.of());
        when(tab.getPaletteSelectionService()).thenReturn(mock(PaletteSelectionService.class));
        when(tab.findStampOf(any())).thenReturn(Optional.of(stamp));
        when(tab.boundsOf(any())).thenAnswer(invocation -> Optional.of(stamp.boundsOf(invocation.getArgument(0))));
        grid = new LevelSceneEditorGrid(tab, new SpawnPointSprite(), new EnemyTilesAssembler());
        grid.init();
    }

    @Test
    @DisplayName("An enemy is drawn at its pixel position: 5 level pixels right and 3 down of its cell")
    void anEnemyIsDrawnAtItsPixel() {
        // Prepare
        placements.add(new EnemyPlacement("goomba", 2 * TILE_SPRITE_SIZE + 5, TILE_SPRITE_SIZE + 3));

        // Execute
        final BufferedImage image = paint();

        // Verify - a level pixel is 4 editor pixels
        final int left = 2 * TILE_SIZE + 20;
        final int top = TILE_SIZE + 12;
        assertThat(image.getRGB(left + 1, top + 1)).as("Just inside its top-left").isEqualTo(RED);
        assertThat(image.getRGB(left - 1, top + 1)).as("Left of it").isNotEqualTo(RED);
        assertThat(image.getRGB(left + 1, top - 1)).as("Above it").isNotEqualTo(RED);
        assertThat(image.getRGB(left + TILE_SIZE - 1, top + TILE_SIZE - 1)).as("Its bottom-right").isEqualTo(RED);
        assertThat(image.getRGB(left + TILE_SIZE + 1, top + 1)).as("Right of it").isNotEqualTo(RED);
    }

    @Test
    @DisplayName("The selected enemy is outlined, the others are not")
    void theSelectedEnemyIsOutlined() {
        // Prepare
        final EnemyPlacement selected = new EnemyPlacement("goomba", 0, 0);
        placements.add(selected);
        placements.add(new EnemyPlacement("goomba", 3 * TILE_SPRITE_SIZE, 0));
        when(tab.getSelectedEnemyPlacement()).thenReturn(selected);

        // Execute
        final BufferedImage image = paint();

        // Verify
        assertThat(image.getRGB(TILE_SIZE / 2, 2)).as("The selected one's top edge")
            .isEqualTo(LevelSceneEditorGrid.SELECTED_ENEMY.getRGB());
        assertThat(image.getRGB(3 * TILE_SIZE + TILE_SIZE / 2, 2)).as("The other one").isEqualTo(RED);
    }

    @Test
    @DisplayName("Arrow keys nudge the selected enemy a pixel, and Shift with them four")
    void arrowKeysNudgeTheSelectedEnemy() {
        // Prepare
        when(tab.getSelectedEnemyPlacement()).thenReturn(new EnemyPlacement("goomba", 0, 0));

        // Execute
        press(getKeyStroke(VK_RIGHT, 0));
        press(getKeyStroke(VK_LEFT, SHIFT_DOWN_MASK));

        // Verify
        verify(tab).nudgeSelectedEnemy(1, 0);
        verify(tab).nudgeSelectedEnemy(-4, 0);
    }

    @Test
    @DisplayName("With no enemy selected the arrow keys are left to the scroll pane")
    void arrowKeysAreFreeWithoutASelection() {
        // Execute
        final Action action = actionFor(getKeyStroke(VK_RIGHT, 0));

        // Verify
        assertThat(action.isEnabled()).isFalse();
        verify(tab, never()).nudgeSelectedEnemy(1, 0);
    }

    private void press(final KeyStroke keyStroke) {
        final Action action = actionFor(keyStroke);
        assertThat(action.isEnabled()).as("Bound and enabled").isTrue();
        action.actionPerformed(new ActionEvent(grid, ActionEvent.ACTION_PERFORMED, ""));
    }

    private Action actionFor(final KeyStroke keyStroke) {
        return grid.getActionMap().get(grid.getInputMap(WHEN_FOCUSED).get(keyStroke));
    }

    private KeyStroke getKeyStroke(final int key, final int modifiers) {
        return KeyStroke.getKeyStroke(key, modifiers);
    }

    private BufferedImage paint() {
        final BufferedImage image = new BufferedImage(COLUMNS * TILE_SIZE, ROWS * TILE_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        grid.paintComponent(graphics);
        graphics.dispose();
        return image;
    }

    private Tile redTile() {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, RED);
        return Tile.builder().id(900).type(ENEMY_PART).argbData(pixels).build();
    }

    private Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
