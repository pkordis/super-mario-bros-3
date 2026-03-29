package house.x1337.app.smb3.ui.editor.level.tile.review;

import house.x1337.app.smb3.enumeration.TileType;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Font.BOLD;
import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.WEST;
import static java.awt.event.ItemEvent.SELECTED;
import static java.util.Comparator.comparing;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JSplitPane.HORIZONTAL_SPLIT;
import static javax.swing.border.TitledBorder.LEADING;
import static javax.swing.border.TitledBorder.TOP;

public sealed interface ComponentsBuilder permits NewTilesReviewWindow {
    String NO_TYPE_LABEL = "- (none)";

    void saveSelectedTile();

    default JPanel buildContent(
        final NewTilesList tileList,
        final JLabel headerLabel,
        final JLabel idValueLabel,
        final JComboBox<String> typeCombo,
        final JTextField descriptionField,
        final JButton setButton,
        final NewTileProcessingPanel newTileProcessingPanel
    ) {
        final NewTilesReviewWindow reviewWindow = (NewTilesReviewWindow) this;
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));

        // Header
        root.add(headerLabel, NORTH);

        // Split pane
        final JSplitPane split = new JSplitPane(
            HORIZONTAL_SPLIT,
            buildListPanel(tileList),
            buildFormPanel(
                idValueLabel,
                typeCombo,
                descriptionField,
                setButton,
                newTileProcessingPanel
            )
        );
        split.setDividerLocation(260);
        split.setResizeWeight(0.35);
        root.add(split, CENTER);

        // Footer
        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            reviewWindow.setVisible(false);
            reviewWindow.dispose();
        });

        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);
        root.add(footer, SOUTH);

        // Enable/disable Set button whenever the type combo selection changes
        typeCombo.addItemListener(e -> {
            if (e.getStateChange() == SELECTED) {
                updateSetButtonState(typeCombo, setButton);
            }
        });

        setButton.addActionListener(e -> saveSelectedTile());
        setButton.setEnabled(false);
        headerLabel.setFont(headerLabel.getFont().deriveFont(BOLD, 13f));

        return root;
    }

    default JPanel buildFormPanel(
        final JLabel idValueLabel,
        final JComboBox<String> typeCombo,
        final JTextField descriptionField,
        final JButton setButton,
        final NewTileProcessingPanel newTileProcessingPanel
    ) {
        final NewTilesReviewWindow reviewWindow = (NewTilesReviewWindow) this;
        final JPanel form = new JPanel(new GridBagLayout());
        final TitledBorder border = createTitledBorder(
            createEtchedBorder(), "Tile Properties",
            LEADING,
            TOP
        );
        form.setBorder(border);

        final GridBagConstraints label = new GridBagConstraints();
        label.anchor = WEST;
        label.insets = new Insets(5, 8, 5, 6);
        label.ipadx = 12;
        label.gridx = 0;
        label.gridy = 0;

        final GridBagConstraints field = new GridBagConstraints();
        field.anchor = WEST;
        field.fill = HORIZONTAL;
        field.weightx = 1.0;
        field.insets = new Insets(5, 0, 5, 8);
        field.gridx = 1;
        field.gridy = 0;

        form.add(new JLabel("ID:"), label);
        idValueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        form.add(idValueLabel, field);

        label.gridy++; field.gridy++;
        form.add(new JLabel("Type:"), label);
        form.add(typeCombo, field);

        label.gridy++; field.gridy++;
        form.add(new JLabel("Description:"), label);
        form.add(descriptionField, field);

        // Tile processing pane
        label.gridy++;
        label.gridwidth = 2;
        label.anchor = GridBagConstraints.CENTER;
        label.fill = NONE;
        label.insets = new Insets(10, 8, 5, 8);
        final JPanel procWrapper = new JPanel(new BorderLayout());
        procWrapper.setBorder(createTitledBorder(createEtchedBorder(), "Tile Processing", LEADING, TOP));
        procWrapper.add(newTileProcessingPanel, CENTER);
        form.add(procWrapper, label);

        // Set button
        label.gridy++;
        label.insets = new Insets(12, 8, 5, 8);
        setButton.setPreferredSize(new Dimension(120, 28));
        form.add(setButton, label);

        // Vertical glue
        label.gridy++;
        label.fill = BOTH;
        label.weighty = 1.0;
        form.add(Box.createGlue(), label);

        reviewWindow.setFormEnabled(false);
        return form;
    }

    private JScrollPane buildListPanel(final NewTilesList tileList) {
        final JScrollPane scroll = new JScrollPane(tileList);
        scroll.setBorder(createTitledBorder("Imported Tiles"));
        return scroll;
    }

    default JComboBox<String> buildTypeCombo() {
        final JComboBox<String> combo = new JComboBox<>();
        combo.addItem(NO_TYPE_LABEL);
        TileType
            .getByExcludedCategory(VIRTUAL)
            .stream()
            .sorted(comparing(TileType::getLabel))
            .forEach(type -> combo.addItem(type.getLabel()));
        return combo;
    }

    default void updateSetButtonState(
        final JComboBox<String> typeCombo,
        final JButton setButton
    ) {
        final boolean typeChosen = !NO_TYPE_LABEL.equals(typeCombo.getSelectedItem());
        setButton.setEnabled(typeCombo.isEnabled() && typeChosen);
    }
}
