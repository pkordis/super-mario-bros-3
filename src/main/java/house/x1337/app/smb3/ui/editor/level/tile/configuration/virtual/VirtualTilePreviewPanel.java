package house.x1337.app.smb3.ui.editor.level.tile.configuration.virtual;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.model.ui.tile.TileCapabilities.THUMB_SIZE;
import static java.awt.Color.LIGHT_GRAY;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;

@Prototype
public final class VirtualTilePreviewPanel extends JPanel {
    private Tile tile;

    @PostConstruct
    void init() {
        setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
        setBackground(LIGHT_GRAY);
    }

    void setTile(final Tile newTile) {
        this.tile = newTile;
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        if (tile == null) {
            return;
        }
        final BufferedImage image = tile.toImage();
        if (image == null) {
            return;
        }

        final Graphics2D graphics2d = (Graphics2D) graphics;
        graphics2d.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        final int x = (getWidth() - THUMB_SIZE) / 2;
        final int y = (getHeight() - THUMB_SIZE) / 2;
        graphics2d.drawImage(image, x, y, THUMB_SIZE, THUMB_SIZE, null);
    }
}
