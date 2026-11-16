package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.ui.editor.component.CustomDataTablePanel;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Copy;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Link;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Unlink;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectChoice.NONE;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Font.BOLD;
import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static java.awt.event.ItemEvent.SELECTED;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;

/**
 * The Associate with Existing Level Object section of the tile form. Its dropdown offers every single-tiled level
 * object, plus None, which clears an existing link. The chosen object's description and data can be edited: left as
 * they are, the button links the tile to the object; once changed, it creates a new level object from them and links
 * the tile to that one. The button's label says which it will do.
 */
@Prototype
@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public class LevelObjectAssociationPanel extends JPanel {
    static final String LINK_LABEL = "Link";
    static final String CREATE_AND_LINK_LABEL = "Create New and Link";
    static final String UNLINK_LABEL = "Unlink";

    private final LevelObjectService levelObjectService;
    private final CustomDataTablePanel dataPanel;
    private final JRadioButton radio = new JRadioButton("Associate with existing level object");
    private final JComboBox<LevelObjectChoice> levelObjectCombo = new JComboBox<>();
    private final JTextField descriptionField = new JTextField(22);
    private final JButton saveButton = new JButton(LINK_LABEL);
    private boolean active;
    private boolean linked;

    @PostConstruct
    void init() {
        dataPanel.render("Data");
        levelObjectCombo.addItemListener(event -> {
            if (event.getStateChange() == SELECTED) {
                showChoice();
            }
        });
        descriptionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent event) {
                updateEditing();
            }

            @Override
            public void removeUpdate(final DocumentEvent event) {
                updateEditing();
            }

            @Override
            public void changedUpdate(final DocumentEvent event) {
                updateEditing();
            }
        });
        dataPanel.onChange(this::updateEditing);
        layOut();
        load(null);
    }

    public void load(final Tile tile) {
        linked = tile != null && tile.getLevelObjectId() != null;
        levelObjectCombo.removeAllItems();
        levelObjectCombo.addItem(NONE);
        if (tile != null) {
            for (final LevelObjectRecord levelObject : levelObjectService.getSingleTiledLevelObjects()) {
                final LevelObjectChoice choice = new LevelObjectChoice(levelObject);
                levelObjectCombo.addItem(choice);
                if (Objects.equals(tile.getLevelObjectId(), levelObject.getId())) {
                    levelObjectCombo.setSelectedItem(choice);
                }
            }
        }
        showChoice();
    }

    public void setActive(final boolean active) {
        this.active = active;
        updateEditing();
    }

    public void onSave(final Runnable action) {
        saveButton.addActionListener(event -> action.run());
    }

    public boolean isSavable() {
        return saveButton.isEnabled();
    }

    /** @return whether the description or data shown differ from the chosen level object's */
    public boolean isModified() {
        final LevelObjectRecord levelObject = selectedChoice().levelObject();
        if (levelObject == null) {
            return false;
        }
        return !Objects.equals(description(), blankToNull(levelObject.getDescription()))
            || !dataPanel.getData().equals(asShown(levelObject.getData()));
    }

    public LevelObjectAssociation association() {
        final LevelObjectRecord levelObject = selectedChoice().levelObject();
        if (levelObject == null) {
            return new Unlink();
        }
        if (!isModified()) {
            return new Link(levelObject);
        }
        return new Copy(levelObject, description(), dataPanel.getData());
    }

    /** @return whether the data repeats a key, in which case the author has been told */
    public boolean warnAboutDuplicateKeys(final Component parent) {
        return !selectedChoice().isNone() && dataPanel.warnAboutDuplicateKeys(parent);
    }

    private LevelObjectChoice selectedChoice() {
        final LevelObjectChoice choice = (LevelObjectChoice) levelObjectCombo.getSelectedItem();
        return choice == null ? NONE : choice;
    }

    private void showChoice() {
        final LevelObjectRecord levelObject = selectedChoice().levelObject();
        if (levelObject == null) {
            descriptionField.setText("");
            dataPanel.show(Map.of());
        } else {
            descriptionField.setText(levelObject.getDescription() == null ? "" : levelObject.getDescription());
            dataPanel.show(levelObject.getData() == null ? Map.of() : levelObject.getData());
        }
        updateEditing();
    }

    private void updateEditing() {
        final boolean none = selectedChoice().isNone();
        final boolean editable = active && !none;
        levelObjectCombo.setEnabled(active);
        descriptionField.setEnabled(editable);
        dataPanel.setEditable(editable);
        saveButton.setEnabled(active && (!none || linked));
        if (none) {
            saveButton.setText(UNLINK_LABEL);
        } else {
            saveButton.setText(isModified() ? CREATE_AND_LINK_LABEL : LINK_LABEL);
        }
    }

    private String description() {
        return blankToNull(descriptionField.getText());
    }

    private String blankToNull(final String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // The table holds trimmed strings, so the level object's data is compared in that form
    private Map<String, Object> asShown(final Map<String, Object> data) {
        final Map<String, Object> shown = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((key, value) -> shown.put(key.trim(), value == null ? "" : String.valueOf(value).trim()));
        }
        return shown;
    }

    private void layOut() {
        setLayout(new BorderLayout(4, 4));
        setBorder(createCompoundBorder(createEtchedBorder(), createEmptyBorder(4, 4, 4, 4)));
        radio.setFont(radio.getFont().deriveFont(BOLD));
        add(radio, NORTH);

        final JPanel fields = new JPanel(new GridBagLayout());
        final GridBagConstraints label = new GridBagConstraints();
        label.anchor = WEST;
        label.insets = new Insets(5, 8, 5, 6);
        label.gridx = 0;
        label.gridy = 0;

        final GridBagConstraints field = new GridBagConstraints();
        field.anchor = WEST;
        field.fill = HORIZONTAL;
        field.weightx = 1.0;
        field.insets = new Insets(5, 0, 5, 8);
        field.gridx = 1;
        field.gridy = 0;

        fields.add(new JLabel("Level object:"), label);
        levelObjectCombo.setPreferredSize(new Dimension(340, levelObjectCombo.getPreferredSize().height));
        fields.add(levelObjectCombo, field);

        label.gridy++;
        field.gridy++;
        fields.add(new JLabel("Description:"), label);
        fields.add(descriptionField, field);

        field.gridy++;
        field.gridx = 0;
        field.gridwidth = 2;
        field.fill = BOTH;
        field.weighty = 1.0;
        field.insets = new Insets(5, 8, 5, 8);
        dataPanel.setMinimumSize(new Dimension(320, 200));
        dataPanel.setPreferredSize(new Dimension(320, 240));
        fields.add(dataPanel, field);
        add(fields, CENTER);

        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        saveButton.setPreferredSize(new Dimension(170, 28));
        footer.add(saveButton);
        add(footer, SOUTH);
    }
}
