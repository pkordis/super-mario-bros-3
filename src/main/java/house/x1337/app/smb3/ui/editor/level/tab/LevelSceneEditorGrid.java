package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;

@Setter
@Prototype
@RequiredArgsConstructor
public class LevelSceneEditorGrid extends JPanel {
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
                handle(e);
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
                tab.updateStatus(e.getX() / TILE_SIZE, e.getY() / TILE_SIZE);
            }

            private void handle(final MouseEvent e) {
                final Tile selectedTile = tab.getSelectedTile();
                if (selectedTile == null) return; // editing blocked when no tile is selected
                final int col = e.getX() / TILE_SIZE;
                final int row = e.getY() / TILE_SIZE;
                tab.updateStatus(col, row);
                if (e.getButton() == MouseEvent.BUTTON3) {
                    tab.eraseTile(col, row);
                } else {
                    tab.paintTile(col, row);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
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

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        final Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Paint the environment layers bottom-to-top, skipping any that are hidden.
        for (final LevelSceneLayer layer : tab.getLayers()) {
            if (!layer.isVisible()) {
                continue;
            }
            final Tile[][] tiles = layer.getTiles();
            for (int row = 0; row < tab.getRows(); row++) {
                for (int col = 0; col < tab.getColumns(); col++) {
                    drawTile(graphics, tiles[row][col], col * TILE_SIZE, row * TILE_SIZE);
                }
            }
        }

        // Grid lines
        graphics.setColor(new Color(0, 0, 0, 40));
        graphics.setStroke(new BasicStroke(1f));
        for (int col = 0; col <= tab.getColumns(); col++) {
            graphics.drawLine(col * TILE_SIZE, 0, col * TILE_SIZE, tab.getRows() * TILE_SIZE);
        }
        for (int row = 0; row <= tab.getRows(); row++) {
            graphics.drawLine(0, row * TILE_SIZE, tab.getColumns() * TILE_SIZE, row * TILE_SIZE);
        }

        // Rendering-starter indicator — thick blue border on the marked cell
        if (tab.getRenderingStarterRow() >= 0 && tab.getRenderingStarterColumn() >= 0) {
            graphics.setColor(new Color(30, 100, 255));
            graphics.setStroke(new BasicStroke(4f));
            final int bx = tab.getRenderingStarterColumn() * TILE_SIZE + 2;
            final int by = tab.getRenderingStarterRow() * TILE_SIZE + 2;
            graphics.drawRect(bx, by, TILE_SIZE - 4, TILE_SIZE - 4);
        }

        // Spawn-point indicator — thick light-green border on the marked cell
        if (tab.getSpawnPointRow() >= 0 && tab.getSpawnPointColumn() >= 0) {
            graphics.setColor(new Color(102, 255, 102));
            graphics.setStroke(new BasicStroke(4f));
            final int sx = tab.getSpawnPointColumn() * TILE_SIZE + 2;
            final int sy = tab.getSpawnPointRow() * TILE_SIZE + 2;
            graphics.drawRect(sx, sy, TILE_SIZE - 4, TILE_SIZE - 4);
        }

        graphics.dispose();
    }
}
