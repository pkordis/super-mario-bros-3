package house.x1337.app.smb3.ui.editor.level.pane;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;

import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

@Singleton
public class LeftPane extends JTabbedPane {
    public LeftPane(
        final TilePalettePanel tilePalettePanel,
        final EnemiesPalettePanel enemiesPalettePanel
    ) {
        super(TOP);
        addTab(null, createDummyIcon(), tilePalettePanel, "Tile palette - pick tiles to paint");
        addTab(null, createEnemyIcon(), enemiesPalettePanel, "Enemies - pick an enemy to place");
        setMinimumSize(new Dimension(200, 0));
        setPreferredSize(new Dimension(220, 0));

        // The NPC layer may have become active (or stopped being) while another tab was showing.
        addChangeListener(event -> {
            if (getSelectedComponent() == enemiesPalettePanel) {
                enemiesPalettePanel.syncEnabledState();
            }
        });
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

    /** A small Goomba-brown silhouette, enough to tell the two palettes apart at tab size. */
    private static ImageIcon createEnemyIcon() {
        final int size = 12;
        final BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(173, 97, 34)); // goomba brown
        g2.fillOval(1, 1, size - 2, size - 4);
        g2.setColor(new Color(99, 55, 19)); // darker feet
        g2.fillRect(1, size - 4, 4, 3);
        g2.fillRect(size - 5, size - 4, 4, 3);
        g2.dispose();
        return new ImageIcon(img);
    }
}
