package house.x1337.app.smb3.ui.editor.level.tile.review;

import house.x1337.app.smb3.ui.editor.level.tile.edit.TilePropertiesForm;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Font.BOLD;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JSplitPane.HORIZONTAL_SPLIT;

public sealed interface ComponentsBuilder permits NewTilesReviewWindow {
    default JPanel buildContent(
        final NewTilesList tileList,
        final JLabel headerLabel,
        final TilePropertiesForm form
    ) {
        final NewTilesReviewWindow reviewWindow = (NewTilesReviewWindow) this;
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));

        headerLabel.setFont(headerLabel.getFont().deriveFont(BOLD, 13f));
        root.add(headerLabel, NORTH);

        final JScrollPane listScroll = new JScrollPane(tileList);
        listScroll.setBorder(createTitledBorder("Tiles Without a Type"));
        final JSplitPane split = new JSplitPane(HORIZONTAL_SPLIT, listScroll, form);
        split.setDividerLocation(260);
        split.setResizeWeight(0.35);
        root.add(split, CENTER);

        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> {
            reviewWindow.setVisible(false);
            reviewWindow.dispose();
        });
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);
        root.add(footer, SOUTH);
        return root;
    }
}
