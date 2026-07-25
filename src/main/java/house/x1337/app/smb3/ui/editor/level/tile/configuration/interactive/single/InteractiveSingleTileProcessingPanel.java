package house.x1337.app.smb3.ui.editor.level.tile.configuration.interactive.single;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

@Prototype
public final class InteractiveSingleTileProcessingPanel extends JPanel {
    private static final int PIXEL_SCALE = 10;
    private static final int PANEL_SIZE = TILE_SPRITE_SIZE * PIXEL_SCALE;
    private static final Color CHECKER_LIGHT = new Color(204, 204, 204);
    private static final Color CHECKER_DARK = new Color(153, 153, 153);
    private static final Color GRID_COLOR = new Color(0, 0, 0, 60);

    private final Dimension panelSize = new Dimension(PANEL_SIZE, PANEL_SIZE);
    private int[] displayPixels;

    @PostConstruct
    void init() {
        setPreferredSize(panelSize);
        setMinimumSize(panelSize);
        setMaximumSize(panelSize);
    }

    public void loadTile(final Tile tile) {
        displayPixels = tile == null ? null : tile.getDisplayArgbData();
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            for (int row = 0; row < TILE_SPRITE_SIZE; row++) {
                for (int col = 0; col < TILE_SPRITE_SIZE; col++) {
                    final int px = col * PIXEL_SCALE;
                    final int py = row * PIXEL_SCALE;
                    if (displayPixels == null) {
                        drawTransparentCell(graphics2D, px, py);
                    } else {
                        final int argb = displayPixels[row * TILE_SPRITE_SIZE + col];
                        if ((argb >>> 24) == 0) {
                            drawTransparentCell(graphics2D, px, py);
                        } else {
                            graphics2D.setColor(new Color(argb, true));
                            graphics2D.fillRect(px, py, PIXEL_SCALE, PIXEL_SCALE);
                        }
                    }
                }
            }
            graphics2D.setColor(GRID_COLOR);
            for (int i = 0; i <= TILE_SPRITE_SIZE; i++) {
                graphics2D.drawLine(i * PIXEL_SCALE, 0, i * PIXEL_SCALE, PANEL_SIZE);
                graphics2D.drawLine(0, i * PIXEL_SCALE, PANEL_SIZE, i * PIXEL_SCALE);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    private static void drawTransparentCell(
        final Graphics2D graphics2D,
        final int px,
        final int py
    ) {
        final int half = PIXEL_SCALE / 2;
        graphics2D.setColor(CHECKER_LIGHT);
        graphics2D.fillRect(px, py, half, half);
        graphics2D.setColor(CHECKER_DARK);
        graphics2D.fillRect(px + half, py, half, half);
        graphics2D.setColor(CHECKER_DARK);
        graphics2D.fillRect(px, py + half, half, half);
        graphics2D.setColor(CHECKER_LIGHT);
        graphics2D.fillRect(px + half, py + half, half, half);
    }
}
