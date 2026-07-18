package house.x1337.app.smb3.ui.editor.level.tile.palette.core;

import house.x1337.app.smb3.enumeration.TileType;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.Component.LEFT_ALIGNMENT;
import static java.awt.FlowLayout.LEFT;
import static java.awt.Font.BOLD;
import static java.lang.Integer.MAX_VALUE;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createMatteBorder;
import static javax.swing.BoxLayout.Y_AXIS;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;

public interface ComponentsBuilder {
    default JLabel buildGroupHeader(final TileType type) {
        final JLabel header = new JLabel(type.getLabel());
        header.setFont(header.getFont().deriveFont(BOLD, 11f));
        header.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(MAX_VALUE, 26));
        header.setBorder(createCompoundBorder(
            createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
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
        final JPanel tilesPanel = getBean(ScrollableFlowPanel.class);
        tilesPanel.setLayout(new BoxLayout(tilesPanel, Y_AXIS));
        tilesPanel.setBorder(createEmptyBorder(4, 4, 4, 4));
        return tilesPanel;
    }

    default JPanel buildGridPanel() {
        final JPanel gridPanel = new JPanel(getBean(WrapLayout.class));
        gridPanel.setAlignmentX(LEFT_ALIGNMENT);
        gridPanel.setBorder(createEmptyBorder(2, 0, 8, 0));
        return gridPanel;
    }
}
