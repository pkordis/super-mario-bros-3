package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyTilesAssembler;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.SPAWN_POINT;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.MouseEvent.BUTTON1;
import static java.awt.event.MouseEvent.MOUSE_EXITED;
import static java.awt.event.MouseEvent.MOUSE_MOVED;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The selected palette item is drawn half transparent under the pointer, exactly where a left click would place it,
 * and only the cells the preview leaves or enters are repainted.
 *
 * <p>The scene is 6 columns by 5 rows, every layer empty.
 */
class LevelSceneEditorGridPreviewTest {
    private static final int ROWS = 5;
    private static final int COLUMNS = 6;
    private static final int RED = 0xFFFF0000;

    private final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
    private final PaletteSelectionService selection = mock(PaletteSelectionService.class);
    private final Map<LevelSceneLayerType, LevelSceneLayer> layers = new EnumMap<>(LevelSceneLayerType.class);
    private LevelSceneEditorGrid grid;

    @BeforeEach
    void prepare() {
        for (final LevelSceneLayerType type : List.of(STATIC_ENVIRONMENT, NON_PLAYABLE_CHARACTERS)) {
            layers.put(type, LevelSceneLayer.builder().type(type).visible(true).tiles(empty()).build());
        }
        when(tab.getLayers()).thenReturn(List.copyOf(layers.values()));
        when(tab.getLevelSceneDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(tab.getPipePortals()).thenReturn(List.of());
        when(tab.getPaletteSelectionService()).thenReturn(selection);
        when(tab.spawnPointFits(any())).thenAnswer(invocation -> invocation.<Coordinates>getArgument(0).y() >= 0);
        activate(STATIC_ENVIRONMENT);
        grid = spy(new LevelSceneEditorGrid(tab, new SpawnPointSprite(), new EnemyTilesAssembler()));
        grid.init();
    }

    @Test
    @DisplayName("A tile shows half transparent on the hovered cell, only on a layer it can be painted on")
    void tileShowsOnTheHoveredCell() {
        // Prepare
        when(selection.getSelectedTile()).thenReturn(redTile(SOLID));
        final BufferedImage before = paint();

        // Execute
        moveTo(of(1, 1));
        final BufferedImage after = paint();
        when(selection.getSelectedTile()).thenReturn(redTile(OBJECT_INTERACTIVE_SINGLE));
        final BufferedImage otherLayersTile = paint();

        // Verify
        assertThat(isHalfRed(pixel(after, of(1, 1)), pixel(before, of(1, 1))))
            .as("Half way between the background and the tile")
            .isTrue();
        assertThat(pixel(after, of(2, 1))).as("The next cell").isEqualTo(pixel(before, of(2, 1)));
        assertThat(sameImage(otherLayersTile, before)).as("A tile of another layer shows nothing").isTrue();
    }

    @Test
    @DisplayName("An enemy shows its whole grid, its rendering starter part on the hovered cell")
    void enemyShowsAroundItsRenderingStarter() {
        // Prepare - a 2x2 enemy anchored on its bottom-left part
        final Tile part = redTile(ENEMY_PART);
        when(selection.getSelectedEnemy()).thenReturn(EnemyStamp
            .builder()
            .enemyType(GOOMBA_NORMAL)
            .tiles(new Tile[][] {{part, part}, {part, part}})
            .renderingStarter(of(0, 1))
            .build());
        activate(NON_PLAYABLE_CHARACTERS);
        final BufferedImage before = paint();

        // Execute
        moveTo(of(1, 2));
        final BufferedImage after = paint();

        // Verify
        for (final Coordinates cell : List.of(of(1, 1), of(2, 1), of(1, 2), of(2, 2))) {
            assertThat(isHalfRed(pixel(after, cell), pixel(before, cell))).as("Cell %s", cell).isTrue();
        }
        assertThat(pixel(after, of(3, 2))).as("Right of the enemy").isEqualTo(pixel(before, of(3, 2)));
        assertThat(pixel(after, of(1, 3))).as("Below the enemy").isEqualTo(pixel(before, of(1, 3)));
    }

    @Test
    @DisplayName("An armed enemy follows the pointer to the level pixel, and with Ctrl held snaps to the cell")
    void enemyFollowsThePointerPixel() {
        // Prepare - a one-tile enemy
        when(selection.getSelectedEnemy()).thenReturn(oneTileEnemy());
        activate(NON_PLAYABLE_CHARACTERS);
        final BufferedImage before = paint();

        // Execute - the pointer on editor pixel (100, 140), which is level pixel (25, 35) in cell (1, 2)
        pointerAt(100, 140, 0);
        final BufferedImage free = paint();
        pointerAt(100, 140, CTRL_DOWN_MASK);
        final BufferedImage snapped = paint();

        // Verify - free, the pointer is in the middle of the part: level pixels (17, 27) to (32, 42)
        assertThat(isHalfRed(free.getRGB(68, 108), before.getRGB(68, 108))).as("Its top-left").isTrue();
        assertThat(free.getRGB(67, 108)).as("Left of it").isEqualTo(before.getRGB(67, 108));
        assertThat(isHalfRed(free.getRGB(131, 171), before.getRGB(131, 171))).as("Its bottom-right").isTrue();
        assertThat(free.getRGB(132, 171)).as("Right of it").isEqualTo(before.getRGB(132, 171));
        assertThat(isHalfRed(snapped.getRGB(64, 128), before.getRGB(64, 128))).as("Snapped top-left").isTrue();
        assertThat(snapped.getRGB(63, 128)).as("Left of the cell").isEqualTo(before.getRGB(63, 128));
        assertThat(snapped.getRGB(128, 191)).as("Right of the cell").isEqualTo(before.getRGB(128, 191));
        verify(tab).updateStatus(of(1, 2), of(17, 27));
    }

    @Test
    @DisplayName("A click places the armed enemy on the pixel its preview showed")
    void aClickPlacesTheEnemyOnThePreviewedPixel() {
        // Prepare
        when(selection.getSelectedEnemy()).thenReturn(oneTileEnemy());
        when(tab.hasPlaceableSelection()).thenReturn(true);
        activate(NON_PLAYABLE_CHARACTERS);

        // Execute
        grid.getMouseListeners()[0]
            .mousePressed(new MouseEvent(grid, MOUSE_PRESSED, 0, 0, 100, 140, 1, false, BUTTON1));

        // Verify
        verify(tab).paintTile(of(1, 2), of(17, 27));
    }

    @Test
    @DisplayName("A spawn point shows above the hovered cell, and nothing on the top row")
    void spawnPointShowsAboveTheHoveredCell() {
        // Prepare
        when(selection.getSelectedVirtualObject()).thenReturn(SPAWN_POINT);
        activate(NON_PLAYABLE_CHARACTERS);
        final BufferedImage before = paint();

        // Execute
        moveTo(of(2, 0));
        final BufferedImage onTopRow = paint();
        moveTo(of(2, 3));
        final BufferedImage below = paint();

        // Verify
        assertThat(sameImage(onTopRow, before)).isTrue();
        assertThat(isGreenish(below.getRGB(2 * TILE_SIZE + TILE_SIZE / 2, 2 * TILE_SIZE + 1)))
            .as("The border's top edge on the row above")
            .isTrue();
        assertThat(isGreenish(below.getRGB(2 * TILE_SIZE + TILE_SIZE / 2, 4 * TILE_SIZE - 2)))
            .as("The border's bottom edge on the hovered row")
            .isTrue();
    }

    @Test
    @DisplayName("Nothing shows once the pointer leaves the grid, or while the active layer is hidden")
    void nothingShowsOffTheGridOrOnAHiddenLayer() {
        // Prepare
        when(selection.getSelectedTile()).thenReturn(redTile(SOLID));
        final BufferedImage before = paint();
        moveTo(of(1, 1));

        // Execute
        layers.get(STATIC_ENVIRONMENT).setVisible(false);
        final BufferedImage hidden = paint();
        layers.get(STATIC_ENVIRONMENT).setVisible(true);
        grid.getMouseListeners()[0].mouseExited(event(MOUSE_EXITED, of(1, 1)));
        final BufferedImage left = paint();

        // Verify
        assertThat(sameImage(hidden, before)).as("Hidden layer").isTrue();
        assertThat(sameImage(left, before)).as("Pointer gone").isTrue();
    }

    @Test
    @DisplayName("Only the old and new preview cells are repainted, and only when the hovered cell changes")
    void repaintsOnlyThePreviewArea() {
        // Prepare
        when(selection.getSelectedTile()).thenReturn(redTile(SOLID));
        moveTo(of(1, 1));
        clearInvocations(grid);

        // Execute & Verify
        grid.getMouseMotionListeners()[0].mouseMoved(new MouseEvent(grid, MOUSE_MOVED, 0, 0, 70, 70, 0, false));
        verify(grid, never()).repaint(any(Rectangle.class));
        moveTo(of(2, 1));
        verify(grid).repaint(new Rectangle(TILE_SIZE, TILE_SIZE, 2 * TILE_SIZE, TILE_SIZE));
    }

    @Test
    @DisplayName("Clearing the selection repaints the cells the preview covered")
    void clearingTheSelectionRepaintsThePreview() {
        // Prepare - a vertical pipe portal hovered on row 3 covers rows 2 and 3
        when(selection.getSelectedVirtualObject()).thenReturn(PIPE_PORTAL_VERTICAL);
        activate(NON_PLAYABLE_CHARACTERS);
        moveTo(of(4, 3));
        clearInvocations(grid);
        when(selection.getSelectedVirtualObject()).thenReturn((VirtualObjectType) null);

        // Execute
        grid.refreshPreview();

        // Verify
        verify(grid).repaint(new Rectangle(4 * TILE_SIZE, 2 * TILE_SIZE, TILE_SIZE, 2 * TILE_SIZE));
    }

    private void activate(final LevelSceneLayerType type) {
        when(tab.getActiveLayer()).thenReturn(layers.get(type));
    }

    private EnemyStamp oneTileEnemy() {
        return EnemyStamp
            .builder()
            .id("goomba")
            .enemyType(GOOMBA_NORMAL)
            .tiles(new Tile[][] {{redTile(ENEMY_PART)}})
            .renderingStarter(of(0, 0))
            .build();
    }

    private void pointerAt(final int x, final int y, final int modifiers) {
        grid.getMouseMotionListeners()[0].mouseMoved(new MouseEvent(grid, MOUSE_MOVED, 0, modifiers, x, y, 0, false));
    }

    private void moveTo(final Coordinates cell) {
        grid.getMouseMotionListeners()[0].mouseMoved(event(MOUSE_MOVED, cell));
    }

    private MouseEvent event(final int id, final Coordinates cell) {
        return new MouseEvent(grid, id, 0, 0, cell.x() * TILE_SIZE + 5, cell.y() * TILE_SIZE + 5, 0, false);
    }

    private BufferedImage paint() {
        final BufferedImage image = new BufferedImage(COLUMNS * TILE_SIZE, ROWS * TILE_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        grid.paintComponent(graphics);
        graphics.dispose();
        return image;
    }

    private int pixel(final BufferedImage image, final Coordinates cell) {
        return image.getRGB(cell.x() * TILE_SIZE + 20, cell.y() * TILE_SIZE + 20);
    }

    /** Whether {@code drawn} is red laid at half opacity over {@code under}. */
    private boolean isHalfRed(final int drawn, final int under) {
        final Color expected = blend(new Color(RED, true), new Color(under, true));
        final Color actual = new Color(drawn, true);
        return Math.abs(actual.getRed() - expected.getRed()) <= 2
            && Math.abs(actual.getGreen() - expected.getGreen()) <= 2
            && Math.abs(actual.getBlue() - expected.getBlue()) <= 2
            && Math.abs(actual.getAlpha() - expected.getAlpha()) <= 2;
    }

    private Color blend(final Color source, final Color destination) {
        final double sourceAlpha = PlacementPreview.ALPHA;
        final double destinationAlpha = destination.getAlpha() / 255.0;
        final double alpha = sourceAlpha + destinationAlpha * (1 - sourceAlpha);
        return new Color(
            (int) Math.round(mix(source.getRed(), destination.getRed(), sourceAlpha, destinationAlpha, alpha)),
            (int) Math.round(mix(source.getGreen(), destination.getGreen(), sourceAlpha, destinationAlpha, alpha)),
            (int) Math.round(mix(source.getBlue(), destination.getBlue(), sourceAlpha, destinationAlpha, alpha)),
            (int) Math.round(alpha * 255)
        );
    }

    private double mix(
        final int source,
        final int destination,
        final double sourceAlpha,
        final double destinationAlpha,
        final double alpha
    ) {
        return (source * sourceAlpha + destination * destinationAlpha * (1 - sourceAlpha)) / alpha;
    }

    private boolean isGreenish(final int argb) {
        final Color colour = new Color(argb, true);
        return colour.getGreen() > 100 && colour.getGreen() > colour.getRed() + 40;
    }

    private boolean sameImage(final BufferedImage first, final BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Tile redTile(final TileType type) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, RED);
        return Tile.builder().id(type.ordinal() + 900).type(type).argbData(pixels).build();
    }

    private Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
