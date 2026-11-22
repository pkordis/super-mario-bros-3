package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.component.FontCapabilities;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyTilesAssembler;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Setter;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SCALE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.LEVEL_COMPLETION_CARD;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.RENDERING_STARTER;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.SPAWN_POINT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.Color.BLACK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.event.KeyEvent.VK_UP;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
import static java.lang.Math.clamp;
import static javax.swing.KeyStroke.getKeyStroke;

@Setter
@Prototype
public class LevelSceneEditorGrid extends JPanel implements FontCapabilities, GridBoxDrawing {
    static final Font PIPE_PORTAL_LABEL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 16);
    static final int COARSE_NUDGE_PIXELS = 4;
    static final Color SELECTED_ENEMY = new Color(0, 200, 255);

    private final Map<Integer, BufferedImage> tileImageCache = new HashMap<>();
    private final LevelSceneEditorTab tab;
    private final SpawnPointSprite spawnPointSprite;
    private final EnemyImages enemyImages;
    private final PlacementPreview placementPreview;
    private final BlockerFlash blockerFlash;
    @Setter(AccessLevel.NONE)
    private Coordinates hoveredCell;
    @Setter(AccessLevel.NONE)
    private Coordinates hoveredEnemyPixel;
    @Setter(AccessLevel.NONE)
    private Rectangle paintedPreviewBox;

    public LevelSceneEditorGrid(
        final LevelSceneEditorTab tab,
        final SpawnPointSprite spawnPointSprite,
        final EnemyTilesAssembler enemyTilesAssembler
    ) {
        this.tab = tab;
        this.spawnPointSprite = spawnPointSprite;
        enemyImages = new EnemyImages(enemyTilesAssembler);
        placementPreview = new PlacementPreview(tab, spawnPointSprite, enemyImages, this::imageOf);
        blockerFlash = new BlockerFlash(this::repaint, System::currentTimeMillis);
    }

    @PostConstruct
    void init() {
        setBackground(new Color(255, 255, 255));
        setFocusable(true);
        bindNudgeKeys();

        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                requestFocusInWindow();
                blockerFlash.beginGesture();
                hover(e);
                if (e.getButton() != MouseEvent.BUTTON3) {
                    tab.selectEnemyAt(levelPixelUnder(e));
                }
                handle(e);
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                hover(e);
                // A pipe portal opens a dialog per placement, so every virtual object is placed on a press only.
                if (tab.isPlacingVirtualObject()) {
                    return;
                }
                handle(e);
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
                hover(e);
                tab.updateStatus(hoveredCell, hoveredEnemyPixel);
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                hover(null, null);
            }

            private void handle(final MouseEvent e) {
                // Editing is blocked while nothing is armed — no tile and no enemy selected.
                if (!tab.hasPlaceableSelection()) {
                    return;
                }
                final Coordinates cell = cellUnder(e);
                tab.updateStatus(cell, enemyPixelUnder(e));
                if (e.getButton() == MouseEvent.BUTTON3) {
                    tab.eraseAt(cell, levelPixelUnder(e));
                } else {
                    tab.paintTile(cell, enemyPixelUnder(e));
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private static Coordinates cellUnder(final MouseEvent e) {
        return of(e.getX() / TILE_SIZE, e.getY() / TILE_SIZE);
    }

    private Coordinates levelPixelUnder(final MouseEvent e) {
        return of(e.getX() / TILE_SCALE, e.getY() / TILE_SCALE);
    }

    /**
     * Where an armed enemy goes for the pointer of {@code e}: the pointer in the middle of its rendering starter part,
     * to the level pixel, or exactly on the cell with Ctrl held or for a stamp placed on the grid. {@code null} while
     * no enemy is armed.
     */
    private Coordinates enemyPixelUnder(final MouseEvent e) {
        final EnemyStamp armed = tab.getPaletteSelectionService().getSelectedEnemy();
        if (armed == null) {
            return null;
        }
        if (e.isControlDown() || (armed.getEnemyType() != null && armed.getEnemyType().isPlacedOnTheGrid())) {
            final Coordinates cell = cellUnder(e);
            return of(cell.x() * TILE_SPRITE_SIZE, cell.y() * TILE_SPRITE_SIZE);
        }
        final Coordinates pointer = levelPixelUnder(e);
        return of(pointer.x() - TILE_SPRITE_SIZE / 2, pointer.y() - TILE_SPRITE_SIZE / 2);
    }

    /**
     * Arrow keys nudge the selected enemy by a pixel, by {@value #COARSE_NUDGE_PIXELS} with Shift, and Escape drops
     * the selection. While no enemy is selected the keys are left to the scroll pane.
     */
    private void bindNudgeKeys() {
        bindNudgeKey(VK_LEFT, -1, 0);
        bindNudgeKey(VK_RIGHT, 1, 0);
        bindNudgeKey(VK_UP, 0, -1);
        bindNudgeKey(VK_DOWN, 0, 1);
        bindKey(getKeyStroke(VK_ESCAPE, 0), "dropEnemySelection", () -> {
            tab.setSelectedEnemyPlacement(null);
            tab.updateStatus(hoveredCell);
            repaint();
        });
    }

    private void bindNudgeKey(final int key, final int dx, final int dy) {
        bindKey(getKeyStroke(key, 0), "nudge" + key, () -> nudgeSelectedEnemy(dx, dy));
        bindKey(
            getKeyStroke(key, SHIFT_DOWN_MASK),
            "nudgeCoarse" + key,
            () -> nudgeSelectedEnemy(dx * COARSE_NUDGE_PIXELS, dy * COARSE_NUDGE_PIXELS)
        );
    }

    private void bindKey(final KeyStroke keyStroke, final String name, final Runnable action) {
        getInputMap(WHEN_FOCUSED).put(keyStroke, name);
        getActionMap().put(name, new SelectedEnemyAction(action));
    }

    private void nudgeSelectedEnemy(final int dx, final int dy) {
        blockerFlash.beginGesture();
        tab.nudgeSelectedEnemy(dx, dy);
        tab.updateStatus(hoveredCell);
    }

    /** A key action that only takes the key while an enemy is selected. */
    private final class SelectedEnemyAction extends AbstractAction {
        private final transient Runnable action;

        private SelectedEnemyAction(final Runnable action) {
            this.action = action;
        }

        @Override
        public boolean isEnabled() {
            return tab.getSelectedEnemyPlacement() != null;
        }

        @Override
        public void actionPerformed(final ActionEvent event) {
            action.run();
        }
    }

    private void hover(final MouseEvent e) {
        hover(cellUnder(e), enemyPixelUnder(e));
    }

    private void hover(final Coordinates cell, final Coordinates enemyPixel) {
        if (Objects.equals(cell, hoveredCell) && Objects.equals(enemyPixel, hoveredEnemyPixel)) {
            return;
        }
        hoveredCell = cell;
        hoveredEnemyPixel = enemyPixel;
        refreshPreview();
    }

    /**
     * Repaints the preview's old and new area, after the hovered cell or enemy pixel, the selection or the active layer
     * changed.
     */
    public void refreshPreview() {
        final Rectangle box = hoveredCell == null
            ? null
            : placementPreview.boxAt(hoveredCell, hoveredEnemyPixel).orElse(null);
        Stream.of(paintedPreviewBox, box)
            .filter(Objects::nonNull)
            .reduce(Rectangle::union)
            .ifPresent(this::repaint);
        paintedPreviewBox = box;
    }

    public void refusePlacement(final List<PixelArea> blockers) {
        blockerFlash.refuse(blockers);
    }

    /** Records an object placed during the current press and drag, which then never blinks as a blocker. */
    public void notePlacement(final PixelArea placed) {
        blockerFlash.notePlaced(placed);
    }

    public void dispose() {
        blockerFlash.stop();
    }

    private BufferedImage imageOf(final Tile tile) {
        return tileImageCache.computeIfAbsent(tile.getId(), id -> tile.toImage());
    }

    private void drawTile(
        final Graphics2D graphics,
        final Tile tile,
        final int x,
        final int y
    ) {
        if (tile == null || tile == NULL_TILE) {
            return;
        }
        graphics.drawImage(imageOf(tile), x, y, TILE_SIZE, TILE_SIZE, null);
    }

    /**
     * Drops the cached image for the given tile id so the next paint re-renders it from the
     * tile's current pixel data (used after a tile is classified and its {@code argbData} changes).
     */
    public void invalidateTile(final int tileId) {
        tileImageCache.remove(tileId);
    }

    /**
     * The scene cells a paint must cover to fill {@code clip}, the whole scene when there is no clip. The range never
     * leaves the scene, even where the panel is stretched past it.
     */
    CellRange visibleCells(final Rectangle clip) {
        final int lastColumn = tab.getLevelSceneDimensions().columns() - 1;
        final int lastRow = tab.getLevelSceneDimensions().rows() - 1;
        if (clip == null) {
            return new CellRange(0, lastColumn, 0, lastRow);
        }
        return new CellRange(
            clamp(clip.x / TILE_SIZE, 0, lastColumn),
            clamp((clip.x + clip.width - 1) / TILE_SIZE, 0, lastColumn),
            clamp(clip.y / TILE_SIZE, 0, lastRow),
            clamp((clip.y + clip.height - 1) / TILE_SIZE, 0, lastRow)
        );
    }

    record CellRange(int firstColumn, int lastColumn, int firstRow, int lastRow) {
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        final Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Paint the environment layers bottom-to-top, skipping any that are hidden. Only the cells in the clip are
        // drawn, so a large scene costs no more to repaint than the part on screen.
        final CellRange visible = visibleCells(graphics.getClipBounds());
        for (final LevelSceneLayer layer : tab.getLayers()) {
            if (!layer.isVisible()) {
                continue;
            }
            final Tile[][] tiles = layer.getTiles();
            for (int row = visible.firstRow(); row <= visible.lastRow(); row++) {
                for (int col = visible.firstColumn(); col <= visible.lastColumn(); col++) {
                    drawTile(graphics, tiles[row][col], col * TILE_SIZE, row * TILE_SIZE);
                }
            }
            if (layer.getType() == NON_PLAYABLE_CHARACTERS) {
                drawEnemies(graphics, layer.getEnemyPlacements());
            }
        }

        // Grid lines
        graphics.setColor(new Color(0, 0, 0, 40));
        graphics.setStroke(new BasicStroke(1f));
        final int top = visible.firstRow() * TILE_SIZE;
        final int bottom = (visible.lastRow() + 1) * TILE_SIZE;
        final int left = visible.firstColumn() * TILE_SIZE;
        final int right = (visible.lastColumn() + 1) * TILE_SIZE;
        for (int col = visible.firstColumn(); col <= visible.lastColumn() + 1; col++) {
            graphics.drawLine(col * TILE_SIZE, top, col * TILE_SIZE, bottom);
        }
        for (int row = visible.firstRow(); row <= visible.lastRow() + 1; row++) {
            graphics.drawLine(left, row * TILE_SIZE, right, row * TILE_SIZE);
        }

        drawPipePortals(graphics);
        drawSpawnPoint(graphics);
        drawRenderingStarter(graphics);
        drawLevelCompletionCard(graphics);
        drawSelectedEnemy(graphics);
        if (hoveredCell != null) {
            placementPreview.paint(graphics, hoveredCell, hoveredEnemyPixel);
        }
        blockerFlash.paint(graphics);

        graphics.dispose();
    }

    /** Draws each placed enemy, as one image, at its pixel position: enemies are not bound to the tile grid. */
    private void drawEnemies(final Graphics2D graphics, final List<EnemyPlacement> enemies) {
        final Rectangle clip = graphics.getClipBounds();
        for (final EnemyPlacement enemy : enemies) {
            tab.findStampOf(enemy).ifPresent(stamp -> {
                final Rectangle box = pixelBox(stamp.boundsOf(enemy));
                if (clip == null || clip.intersects(box)) {
                    graphics.drawImage(enemyImages.of(stamp), box.x, box.y, box.width, box.height, null);
                }
            });
        }
    }

    private void drawSelectedEnemy(final Graphics2D graphics) {
        final EnemyPlacement selected = tab.getSelectedEnemyPlacement();
        if (selected != null && isNpcLayerVisible()) {
            tab.boundsOf(selected).ifPresent(bounds -> drawBorder(graphics, SELECTED_ENEMY, pixelBox(bounds)));
        }
    }
    private void drawSpawnPoint(final Graphics2D graphics) {
        final Coordinates upperCell = tab.getSpawnPoint();
        if (!isNpcLayerVisible() || upperCell == null) {
            return;
        }
        final Rectangle box = cellBox(upperCell, SPAWN_POINT);
        spawnPointSprite.draw(graphics, box);
        drawBorder(graphics, SPAWN_POINT.getColour(), box);
    }

    private void drawRenderingStarter(final Graphics2D graphics) {
        final Coordinates cell = tab.getRenderingStarter();
        if (isNpcLayerVisible() && cell != null) {
            drawBorder(graphics, RENDERING_STARTER.getColour(), cellBox(cell, RENDERING_STARTER));
        }
    }

    private void drawLevelCompletionCard(final Graphics2D graphics) {
        if (isNpcLayerVisible() && tab.hasLevelCompletionCard()) {
            drawBorder(
                graphics,
                LEVEL_COMPLETION_CARD.getColour(),
                cellBox(tab.getLevelCompletionCard(), LEVEL_COMPLETION_CARD)
            );
        }
    }

    private Rectangle cellBox(final Coordinates topLeft, final VirtualObjectType type) {
        return new Rectangle(
            topLeft.x() * TILE_SIZE,
            topLeft.y() * TILE_SIZE,
            type.getColumns() * TILE_SIZE,
            type.getRows() * TILE_SIZE
        );
    }

    /**
     * Outlines every pipe portal over all of its cells, above every layer's tiles. Portals belong to the NPC layer,
     * so they are hidden along with it.
     */
    private void drawPipePortals(final Graphics2D graphics) {
        if (!isNpcLayerVisible() || tab.getPipePortals() == null) {
            return;
        }
        graphics.setStroke(new BasicStroke(4f));
        graphics.setFont(PIPE_PORTAL_LABEL_FONT);
        for (final PipePortal portal : tab.getPipePortals()) {
            if (portal.getVirtualObjectType() == null) {
                continue;
            }
            final Color colour = portal.getVirtualObjectType().getColour();
            graphics.setColor(colour);
            graphics.drawRect(
                portal.getCoordinates().x() * TILE_SIZE + 2,
                portal.getTopRow() * TILE_SIZE + 2,
                portal.getColumns() * TILE_SIZE - 4,
                portal.getRows() * TILE_SIZE - 4
            );
            if (portal.getType() != null) {
                drawPipePortalLabel(graphics, portal, colour);
            }
        }
    }

    private void drawPipePortalLabel(
        final Graphics2D graphics,
        final PipePortal portal,
        final Color colour
    ) {
        final String text = portal.getType().getShortLabel();
        final Rectangle area = new Rectangle(
            portal.getCoordinates().x() * TILE_SIZE,
            portal.getTopRow() * TILE_SIZE,
            portal.getColumns() * TILE_SIZE,
            portal.getRows() * TILE_SIZE
        );
        final Point baseline = centredBaseline(graphics.getFontMetrics(), text, area);
        graphics.setColor(BLACK);
        graphics.drawString(text, baseline.x + 1, baseline.y + 1);
        graphics.setColor(colour);
        graphics.drawString(text, baseline.x, baseline.y);
    }

    private boolean isNpcLayerVisible() {
        return tab.getLayers().stream()
            .anyMatch(layer -> layer.getType() == NON_PLAYABLE_CHARACTERS && layer.isVisible());
    }
}
