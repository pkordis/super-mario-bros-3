package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.component.FontCapabilities;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.LEVEL_COMPLETION_CARD;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.Color.BLACK;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;

@Setter
@Prototype
@RequiredArgsConstructor
public class LevelSceneEditorGrid extends JPanel implements FontCapabilities {
    static final Font PIPE_PORTAL_LABEL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 16);

    private final Map<Integer, BufferedImage> tileImageCache = new HashMap<>();
    private final LevelSceneEditorTab tab;

    @PostConstruct
    void init() {
        setPreferredSize(new Dimension(tab.getColumns() * TILE_SIZE, tab.getRows() * TILE_SIZE));
        setBackground(new Color(123, 198, 255));

        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                handle(e);
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                // A pipe portal opens a dialog per placement, so every virtual object is placed on a press only.
                if (tab.isPlacingVirtualObject()) {
                    return;
                }
                handle(e);
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
                tab.updateStatus(cellUnder(e));
            }

            private void handle(final MouseEvent e) {
                // Editing is blocked while nothing is armed — no tile and no enemy selected.
                if (!tab.hasPlaceableSelection()) {
                    return;
                }
                final Coordinates cell = cellUnder(e);
                tab.updateStatus(cell);
                if (e.getButton() == MouseEvent.BUTTON3) {
                    tab.eraseTile(cell);
                } else {
                    tab.paintTile(cell);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private static Coordinates cellUnder(final MouseEvent e) {
        return of(e.getX() / TILE_SIZE, e.getY() / TILE_SIZE);
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
        final BufferedImage cachedImage = tileImageCache.computeIfAbsent(tile.getId(), id -> tile.toImage());
        graphics.drawImage(cachedImage, x, y, TILE_SIZE, TILE_SIZE, null);
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
        final int lastColumn = tab.getColumns() - 1;
        final int lastRow = tab.getRows() - 1;
        if (clip == null) {
            return new CellRange(0, lastColumn, 0, lastRow);
        }
        return new CellRange(
            Math.clamp(clip.x / TILE_SIZE, 0, lastColumn),
            Math.clamp((clip.x + clip.width - 1) / TILE_SIZE, 0, lastColumn),
            Math.clamp(clip.y / TILE_SIZE, 0, lastRow),
            Math.clamp((clip.y + clip.height - 1) / TILE_SIZE, 0, lastRow)
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

        // Rendering-starter indicator — thick blue border on the marked cell
        if (tab.hasRenderingStarter()) {
            graphics.setColor(new Color(30, 100, 255));
            graphics.setStroke(new BasicStroke(4f));
            final int bx = tab.getRenderingStarter().x() * TILE_SIZE + 2;
            final int by = tab.getRenderingStarter().y() * TILE_SIZE + 2;
            graphics.drawRect(bx, by, TILE_SIZE - 4, TILE_SIZE - 4);
        }

        // Spawn-point indicator — thick light-green border on the marked cell
        if (tab.hasSpawnPoint()) {
            graphics.setColor(new Color(102, 255, 102));
            graphics.setStroke(new BasicStroke(4f));
            final int sx = tab.getSpawnPoint().x() * TILE_SIZE + 2;
            final int sy = tab.getSpawnPoint().y() * TILE_SIZE + 2;
            graphics.drawRect(sx, sy, TILE_SIZE - 4, TILE_SIZE - 4);
        }

        drawPipePortals(graphics);
        drawLevelCompletionCard(graphics);

        graphics.dispose();
    }

    /**
     * Outlines the scene's level completion card on its cell, above the pipe portals. The card belongs to the NPC
     * layer, so it is hidden along with it.
     */
    private void drawLevelCompletionCard(final Graphics2D graphics) {
        if (!isNpcLayerVisible() || !tab.hasLevelCompletionCard()) {
            return;
        }
        graphics.setColor(LEVEL_COMPLETION_CARD.getColour());
        graphics.setStroke(new BasicStroke(4f));
        graphics.drawRect(
            tab.getLevelCompletionCard().x() * TILE_SIZE + 2,
            tab.getLevelCompletionCard().y() * TILE_SIZE + 2,
            LEVEL_COMPLETION_CARD.getColumns() * TILE_SIZE - 4,
            LEVEL_COMPLETION_CARD.getRows() * TILE_SIZE - 4
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
