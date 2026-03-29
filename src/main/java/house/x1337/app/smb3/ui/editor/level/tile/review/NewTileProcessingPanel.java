package house.x1337.app.smb3.ui.editor.level.tile.review;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.awt.Cursor.CROSSHAIR_CURSOR;
import static java.awt.Cursor.getPredefinedCursor;

@Prototype
public final class NewTileProcessingPanel extends JPanel {
    private static final int PIXEL_SCALE = 10;
    private static final int PANEL_SIZE = TILE_SPRITE_SIZE * PIXEL_SCALE;
    private static final Color CHECKER_LIGHT = new Color(204, 204, 204);
    private static final Color CHECKER_DARK = new Color(153, 153, 153);
    private static final Color GRID_COLOR = new Color(0, 0, 0, 60);

    private final Dimension panelSize = new Dimension(PANEL_SIZE, PANEL_SIZE);
    private int[] workingPixels;

    @PostConstruct
    void init() {
        setPreferredSize(panelSize);
        setMinimumSize(panelSize);
        setMaximumSize(panelSize);
        setCursor(getPredefinedCursor(CROSSHAIR_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
    }

    public void loadTile(final Tile tile) {
        final int[] pixels = tile == null ? null : tile.getDisplayArgbData();
        if (pixels == null) {
            workingPixels = null;
        } else {
            workingPixels = pixels.clone();
        }
        repaint();
    }

    public int[] getWorkingPixels() {
        return workingPixels == null ? null : workingPixels.clone();
    }

    private void handleClick(final int screenX, final int screenY) {
        if (workingPixels == null) {
            return;
        }
        final int col = screenX / PIXEL_SCALE;
        final int row = screenY / PIXEL_SCALE;
        if (col < 0 || col >= TILE_SPRITE_SIZE || row < 0 || row >= TILE_SPRITE_SIZE) {
            return;
        }
        final int clickedArgb = workingPixels[row * TILE_SPRITE_SIZE + col];
        if ((clickedArgb >>> 24) == 0) {
            return;
        }
        floodFillTransparent(col, row, clickedArgb);
        repaint();
    }

    private void floodFillTransparent(final int startCol, final int startRow, final int targetArgb) {
        final Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startCol, startRow});
        workingPixels[startRow * TILE_SPRITE_SIZE + startCol] = 0x00000000;

        while (!queue.isEmpty()) {
            final int[] position = queue.poll();
            final int cx = position[0];
            final int cy = position[1];

            for (final int[] direction : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                final int nx = cx + direction[0];
                final int ny = cy + direction[1];
                if (nx < 0 || nx >= TILE_SPRITE_SIZE || ny < 0 || ny >= TILE_SPRITE_SIZE) {
                    continue;
                }
                final int index = ny * TILE_SPRITE_SIZE + nx;
                if (workingPixels[index] == targetArgb) {
                    workingPixels[index] = 0x00000000;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
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
                    if (workingPixels == null) {
                        drawTransparentCell(graphics2D, px, py);
                    } else {
                        final int argb = workingPixels[row * TILE_SPRITE_SIZE + col];
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

