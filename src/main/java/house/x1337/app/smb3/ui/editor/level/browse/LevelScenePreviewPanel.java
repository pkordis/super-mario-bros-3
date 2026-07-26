package house.x1337.app.smb3.ui.editor.level.browse;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.awt.Color.GRAY;
import static java.awt.Font.ITALIC;
import static java.awt.Font.SANS_SERIF;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.lang.Math.max;
import static java.lang.Math.min;

@Prototype
public class LevelScenePreviewPanel extends JPanel {
    private static final String SELECT_SCENE = "Select a scene to preview";
    private static final int PREVIEW_TILES_X = 16;
    private static final int PREVIEW_TILES_Y = 15;
    private static final int PREVIEW_TILE_PX = TILE_SPRITE_SIZE;
    private static final int PREVIEW_WIDTH = PREVIEW_TILES_X * PREVIEW_TILE_PX;
    private static final int PREVIEW_HEIGHT = PREVIEW_TILES_Y * PREVIEW_TILE_PX;

    private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();
    private LevelScene levelScene;

    public void renderPreview(final LevelScene levelScene) {
        this.levelScene = levelScene;
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            if (levelScene == null) {
                drawPlaceholder(graphics2D);
            } else {
                graphics2D.setRenderingHint(
                    KEY_INTERPOLATION,
                    VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                );
                drawTiles(graphics2D);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    private void drawTiles(final Graphics2D graphics2D) {
        final List<LevelSceneLayer> layers = levelScene.getLayersBottomToTop();
        final Integer rsRow = levelScene.getRenderingStarterRow();
        final Integer rsColumn = levelScene.getRenderingStarterColumn();
        final LevelSceneDimensions dimensions = levelScene.getDimensions();
        final int startCol = (rsColumn != null && rsColumn >= 0) ? rsColumn : 0;
        final int startRow;
        if (rsRow != null && rsRow >= 0) {
            startRow = max(0, rsRow - PREVIEW_TILES_Y + 1);
        } else {
            startRow = 0;
        }
        final int rowCount = min(dimensions.rows() - startRow, PREVIEW_TILES_Y);
        final int colCount = min(dimensions.columns() - startCol, PREVIEW_TILES_X);

        final BufferedImage composite = new BufferedImage(
            colCount * PREVIEW_TILE_PX,
            rowCount * PREVIEW_TILE_PX,
            TYPE_INT_ARGB
        );
        final Graphics2D cg = composite.createGraphics();
        cg.setComposite(AlphaComposite.SrcOver);
        cg.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try {
            for (final LevelSceneLayer layer : layers) {
                final Tile[][] tiles = layer.getTiles();
                for (int row = 0; row < rowCount; row++) {
                    for (int col = 0; col < colCount; col++) {
                        final Tile tile = tiles[row + startRow][col + startCol];
                        if (tile.isRenderable()) {
                            drawTile(cg, tile, col * PREVIEW_TILE_PX, row * PREVIEW_TILE_PX);
                        }
                    }
                }
            }
        } finally {
            cg.dispose();
        }
        graphics2D.drawImage(composite, 0, 0, null);
    }

    private void drawTile(
        final Graphics2D graphics,
        final Tile tile,
        final int x,
        final int y
    ) {
        final int[] data = tile.getArgbData();
        final BufferedImage img = spriteCache.computeIfAbsent(tile.getId(), id -> {
            final int s = TILE_SPRITE_SIZE;
            final BufferedImage image = new BufferedImage(s, s, TYPE_INT_ARGB);
            image.setRGB(0, 0, s, s, data, 0, s);
            return image;
        });
        graphics.drawImage(img, x, y, PREVIEW_TILE_PX, PREVIEW_TILE_PX, null);
    }

    private void drawPlaceholder(final Graphics2D graphics) {
        final int stringWidth = graphics.getFontMetrics().stringWidth(SELECT_SCENE);
        graphics.setColor(GRAY);
        graphics.setFont(new Font(SANS_SERIF, ITALIC, 11));
        graphics.drawString(SELECT_SCENE, (PREVIEW_WIDTH - stringWidth) / 2, PREVIEW_HEIGHT / 2);
    }
}
