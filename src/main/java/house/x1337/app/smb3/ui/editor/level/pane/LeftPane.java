package house.x1337.app.smb3.ui.editor.level.pane;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;

import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

@Singleton
public class LeftPane extends JTabbedPane {
    public LeftPane(final TilePalettePanel tilePalettePanel) {
        super(TOP);
        addTab(null, createDummyIcon(), tilePalettePanel, "Tile palette - pick tiles to paint");
        setMinimumSize(new Dimension(200, 0));
        setPreferredSize(new Dimension(220, 0));
    }

    private static ImageIcon createDummyIcon() {
        final int size = 12;
        final BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(100, 149, 237)); // cornflower blue
        g2.fillRect(0, 0, size / 2, size / 2);
        g2.fillRect(size / 2, size / 2, size / 2, size / 2);
        g2.setColor(new Color(70, 130, 180)); // steel blue
        g2.fillRect(size / 2, 0, size / 2, size / 2);
        g2.fillRect(0, size / 2, size / 2, size / 2);
        g2.dispose();
        return new ImageIcon(img);
    }
}
