package house.x1337.app.smb3.ui.editor.level.enemy.create;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.Cursor.CROSSHAIR_CURSOR;
import static java.awt.Cursor.HAND_CURSOR;
import static java.awt.Cursor.getPredefinedCursor;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/**
 * An enemy's parts, magnified, for picking its rendering starter or, with {@link Tool#PIXEL_REMOVER}, clearing pixels
 * of the parts it shows. Pixels are cleared in the part arrays passed to {@link #render}; parts sharing one array show
 * a cleared pixel together.
 */
@Prototype
public final class EnemyTilesGridPanel extends JPanel {
    private static final int PIXEL_SCALE = 8;
    private static final int CELL_SIZE = TILE_SPRITE_SIZE * PIXEL_SCALE;
    private static final int CHECKER_SIZE = PIXEL_SCALE / 2;

    private static final Color CHECKER_LIGHT = new Color(204, 204, 204);
    private static final Color CHECKER_DARK = new Color(153, 153, 153);
    private static final Color GRID_COLOR = new Color(0, 0, 0, 60);
    private static final Color RENDERING_STARTER_COLOR = new Color(30, 100, 255);
    private static final int RENDERING_STARTER_STROKE = 4;
    private static final int TRANSPARENT = 0;

    public enum Tool {
        RENDERING_STARTER,
        PIXEL_REMOVER
    }

    private BufferedImage[][] cellImages;
    private int[][][] partPixels;
    private int rows;
    private int columns;

    @Getter
    private Coordinates renderingStarter;
    @Getter
    private Tool tool = Tool.RENDERING_STARTER;

    public EnemyTilesGridPanel() {
        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent event) {
                if (tool == Tool.RENDERING_STARTER) {
                    moveRenderingStarterTo(of(event.getX() / CELL_SIZE, event.getY() / CELL_SIZE));
                } else {
                    removePixelAt(event.getX(), event.getY());
                }
            }

            @Override
            public void mouseDragged(final MouseEvent event) {
                if (tool == Tool.PIXEL_REMOVER) {
                    removePixelAt(event.getX(), event.getY());
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Shows the parts, the rendering starter on the lower-left one: an enemy is anchored where it meets the ground. */
    public void render(final int[][][] partPixels) {
        render(partPixels, of(0, partPixels.length - 1));
    }

    public void render(
        final int[][][] partPixels,
        final Coordinates renderingStarter
    ) {
        this.partPixels = partPixels;
        this.rows = partPixels.length;
        this.columns = partPixels[0].length;
        this.renderingStarter = renderingStarter;
        refreshCellImages();

        final Dimension size = new Dimension(columns * CELL_SIZE, rows * CELL_SIZE);
        setPreferredSize(size);
        setMinimumSize(size);
        setTool(tool);
    }

    public void setTool(final Tool tool) {
        this.tool = tool;
        setCursor(getPredefinedCursor(tool == Tool.RENDERING_STARTER ? HAND_CURSOR : CROSSHAIR_CURSOR));
    }

    /** Clears the sprite pixel under the panel point ({@code x}, {@code y}) to fully transparent. */
    void removePixelAt(
        final int x,
        final int y
    ) {
        final int column = x / CELL_SIZE;
        final int row = y / CELL_SIZE;
        if (x < 0 || y < 0 || column >= columns || row >= rows) {
            return;
        }
        final int index = (y % CELL_SIZE) / PIXEL_SCALE * TILE_SPRITE_SIZE + (x % CELL_SIZE) / PIXEL_SCALE;
        final int[] pixels = partPixels[row][column];
        if (pixels[index] == TRANSPARENT) {
            return;
        }
        pixels[index] = TRANSPARENT;
        refreshCellImages();
        repaint();
    }

    private void refreshCellImages() {
        cellImages = new BufferedImage[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cellImages[row][column] = toImage(partPixels[row][column]);
            }
        }
    }

    private void moveRenderingStarterTo(final Coordinates cell) {
        if (cell.x() < 0 || cell.x() >= columns || cell.y() < 0 || cell.y() >= rows) {
            return;
        }
        renderingStarter = cell;
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            drawCheckerboard(graphics2D);
            drawCells(graphics2D);
            drawGridLines(graphics2D);
            drawRenderingStarter(graphics2D);
        } finally {
            graphics2D.dispose();
        }
    }

    /**
     * Fills the whole grid with the transparency checkerboard. Drawing it under the parts rather than
     * per transparent pixel gives the same result. The pattern repeats every sprite pixel, so whatever
     * an opaque pixel covers is identical to what it would have covered anyway, for a fraction of the
     * work.
     */
    private void drawCheckerboard(final Graphics2D graphics2D) {
        final int width = columns * CELL_SIZE;
        final int height = rows * CELL_SIZE;
        for (int y = 0; y < height; y += CHECKER_SIZE) {
            for (int x = 0; x < width; x += CHECKER_SIZE) {
                final boolean light = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
                graphics2D.setColor(light ? CHECKER_LIGHT : CHECKER_DARK);
                graphics2D.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
            }
        }
    }

    private void drawCells(final Graphics2D graphics2D) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                graphics2D.drawImage(
                    cellImages[row][column],
                    column * CELL_SIZE,
                    row * CELL_SIZE,
                    CELL_SIZE,
                    CELL_SIZE,
                    null
                );
            }
        }
    }

    private void drawGridLines(final Graphics2D graphics2D) {
        graphics2D.setColor(GRID_COLOR);
        graphics2D.setStroke(new BasicStroke(1f));
        for (int column = 0; column <= columns; column++) {
            graphics2D.drawLine(column * CELL_SIZE, 0, column * CELL_SIZE, rows * CELL_SIZE);
        }
        for (int row = 0; row <= rows; row++) {
            graphics2D.drawLine(0, row * CELL_SIZE, columns * CELL_SIZE, row * CELL_SIZE);
        }
    }

    /** The same indicator the level-scene grid uses for its own rendering starter. */
    private void drawRenderingStarter(final Graphics2D graphics2D) {
        graphics2D.setColor(RENDERING_STARTER_COLOR);
        graphics2D.setStroke(new BasicStroke(RENDERING_STARTER_STROKE));
        final int inset = RENDERING_STARTER_STROKE / 2;
        graphics2D.drawRect(
            renderingStarter.x() * CELL_SIZE + inset,
            renderingStarter.y() * CELL_SIZE + inset,
            CELL_SIZE - RENDERING_STARTER_STROKE,
            CELL_SIZE - RENDERING_STARTER_STROKE
        );
    }

    private static BufferedImage toImage(final int[] argbPixels) {
        final BufferedImage image = new BufferedImage(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE, TYPE_INT_ARGB);
        image.setRGB(0, 0, TILE_SPRITE_SIZE, TILE_SPRITE_SIZE, argbPixels, 0, TILE_SPRITE_SIZE);
        return image;
    }
}
