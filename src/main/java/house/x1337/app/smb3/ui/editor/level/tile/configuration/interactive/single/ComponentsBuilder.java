package house.x1337.app.smb3.ui.editor.level.tile.configuration.interactive.single;

import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;

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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Font.BOLD;
import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static java.awt.event.ItemEvent.SELECTED;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.border.TitledBorder.LEADING;
import static javax.swing.border.TitledBorder.TOP;

public sealed interface ComponentsBuilder permits EditInteractiveSingleTiledWindow {
    String NO_OBJECT_TYPE_LABEL = "- (none)";

    void saveSelectedTile();

    default JPanel buildContent(
        final InteractiveSingleTilesList tileList,
        final JLabel headerLabel,
        final JTextField descriptionField,
        final JComboBox<String> objectTypeCombo,
        final JButton saveButton
    ) {
        final EditInteractiveSingleTiledWindow window = (EditInteractiveSingleTiledWindow) this;
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));

        headerLabel.setFont(headerLabel.getFont().deriveFont(BOLD, 13f));
        root.add(headerLabel, NORTH);

        final JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildListPanel(tileList),
            buildFormPanel(descriptionField, objectTypeCombo, saveButton)
        );
        split.setDividerLocation(260);
        split.setResizeWeight(0.35);
        root.add(split, CENTER);

        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            window.setVisible(false);
            window.dispose();
        });
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);
        root.add(footer, SOUTH);

        objectTypeCombo.addItemListener(e -> {
            if (e.getStateChange() == SELECTED) {
                updateSaveButtonState(objectTypeCombo, saveButton);
            }
        });

        saveButton.addActionListener(e -> saveSelectedTile());
        saveButton.setEnabled(false);

        return root;
    }

    default JPanel buildFormPanel(
        final JTextField descriptionField,
        final JComboBox<String> objectTypeCombo,
        final JButton saveButton
    ) {
        final EditInteractiveSingleTiledWindow window = (EditInteractiveSingleTiledWindow) this;
        final JPanel form = new JPanel(new GridBagLayout());
        final TitledBorder border = createTitledBorder(createEtchedBorder(), "Tile Properties", LEADING, TOP);
        form.setBorder(border);

        final GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = WEST;
        labelGbc.insets = new Insets(5, 8, 5, 6);
        labelGbc.ipadx = 12;
        labelGbc.gridx = 0;
        labelGbc.gridy = 0;

        final GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.anchor = WEST;
        fieldGbc.fill = HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(5, 0, 5, 8);
        fieldGbc.gridx = 1;
        fieldGbc.gridy = 0;

        form.add(new JLabel("Description:"), labelGbc);
        form.add(descriptionField, fieldGbc);

        labelGbc.gridy++; fieldGbc.gridy++;
        form.add(new JLabel("Object Type:"), labelGbc);
        form.add(objectTypeCombo, fieldGbc);

        // Save button
        labelGbc.gridy++;
        labelGbc.gridwidth = 2;
        labelGbc.anchor = GridBagConstraints.CENTER;
        labelGbc.insets = new Insets(12, 8, 5, 8);
        saveButton.setPreferredSize(new Dimension(120, 28));
        form.add(saveButton, labelGbc);

        // Vertical glue
        labelGbc.gridy++;
        labelGbc.fill = BOTH;
        labelGbc.weighty = 1.0;
        form.add(Box.createGlue(), labelGbc);

        window.setFormEnabled(false);
        return form;
    }

    private JScrollPane buildListPanel(final InteractiveSingleTilesList tileList) {
        final JScrollPane scroll = new JScrollPane(tileList);
        scroll.setBorder(createTitledBorder("Interactive Single Tiles"));
        return scroll;
    }

    default JComboBox<String> buildObjectTypeCombo() {
        final JComboBox<String> combo = new JComboBox<>();
        combo.addItem(NO_OBJECT_TYPE_LABEL);
        stream(LevelObjectTypeSingleTiled.values())
            .sorted(comparing(LevelObjectTypeSingleTiled::getLabel))
            .forEach(t -> combo.addItem(t.getLabel()));
        return combo;
    }

    default void updateSaveButtonState(
        final JComboBox<String> objectTypeCombo,
        final JButton saveButton
    ) {
        final boolean typeChosen = !NO_OBJECT_TYPE_LABEL.equals(objectTypeCombo.getSelectedItem());
        saveButton.setEnabled(objectTypeCombo.isEnabled() && typeChosen);
    }
}
