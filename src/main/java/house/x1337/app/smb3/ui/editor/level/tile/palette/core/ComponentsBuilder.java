package house.x1337.app.smb3.ui.editor.level.tile.palette.core;

import house.x1337.app.smb3.enumeration.TileType;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.Dimension;

import static java.awt.Color.DARK_GRAY;
import static java.awt.Color.LIGHT_GRAY;
import static java.awt.Component.LEFT_ALIGNMENT;
import static java.awt.Font.BOLD;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Locale.ROOT;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createMatteBorder;
import static javax.swing.BoxLayout.Y_AXIS;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;

public interface ComponentsBuilder {
    default JLabel buildGroupHeader(final TileType type) {
        final JLabel header = new JLabel(type.getLabel().toUpperCase(ROOT));
        header.setFont(header.getFont().deriveFont(BOLD, 11f));
        header.setForeground(DARK_GRAY);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(MAX_VALUE, 26));
        header.setBorder(createCompoundBorder(
            createMatteBorder(0, 0, 1, 0, LIGHT_GRAY),
            createEmptyBorder(8, 2, 2, 2))
        );
        return header;
    }

    default JScrollPane buildScrollPane(final JPanel tilesPanel) {
        final JScrollPane scrollPane = new JScrollPane(tilesPanel);
        scrollPane.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    default JPanel buildTilesPanel() {
        final JPanel tilesPanel = new JPanel();
        tilesPanel.setLayout(new BoxLayout(tilesPanel, Y_AXIS));
        tilesPanel.setBorder(createEmptyBorder(4, 4, 4, 4));
        return tilesPanel;
    }
}
