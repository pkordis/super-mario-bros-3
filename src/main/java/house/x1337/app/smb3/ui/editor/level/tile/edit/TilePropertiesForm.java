package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static house.x1337.app.smb3.enumeration.TileType.fromLabel;
import static java.awt.BorderLayout.CENTER;
import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.WEST;
import static java.awt.event.ItemEvent.SELECTED;
import static java.util.Comparator.comparing;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.border.TitledBorder.LEADING;
import static javax.swing.border.TitledBorder.TOP;

/**
 * The Tile Properties form shared by the tile windows: the tile's id, type, description and pixels, and Set, which
 * is only enabled once the tile has a type.
 */
@Prototype
@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public class TilePropertiesForm extends JPanel {
    static final String NO_TYPE_LABEL = "- (none)";

    private final TileProcessingPanel processingPanel;
    private final JLabel idValueLabel = new JLabel();
    private final JComboBox<String> typeCombo = new JComboBox<>();
    private final JTextField descriptionField = new JTextField(22);
    private final JButton setButton = new JButton("Set");

    @PostConstruct
    void init() {
        typeCombo.addItem(NO_TYPE_LABEL);
        TileType.getByExcludedCategory(VIRTUAL)
            .stream()
            .sorted(comparing(TileType::getLabel))
            .forEach(type -> typeCombo.addItem(type.getLabel()));
        typeCombo.addItemListener(event -> {
            if (event.getStateChange() == SELECTED) {
                updateSetButton();
            }
        });
        layOut();
        load(null);
    }

    /** Shows the tile, or clears and disables the form when {@code tile} is {@code null}. */
    public void load(final Tile tile) {
        if (tile == null) {
            idValueLabel.setText("");
            idValueLabel.setToolTipText(null);
            typeCombo.setSelectedItem(NO_TYPE_LABEL);
            descriptionField.setText("");
            processingPanel.loadTile(null);
            setFormEnabled(false);
            return;
        }
        // The id shown is the sha256 hash, shortened, with the full value in the tooltip
        final String sha256 = tile.getSha256();
        final String id = sha256 != null ? sha256 : String.valueOf(tile.getId());
        idValueLabel.setText(id.length() > 28 ? id.substring(0, 12) + "..." + id.substring(id.length() - 8) : id);
        idValueLabel.setToolTipText(id);
        typeCombo.setSelectedItem(tile.getType() == null ? NO_TYPE_LABEL : tile.getType().getLabel());
        descriptionField.setText(tile.getDescription() != null ? tile.getDescription() : "");
        processingPanel.loadTile(tile);
        setFormEnabled(true);
    }

    public void onSet(final Runnable action) {
        setButton.addActionListener(event -> action.run());
    }

    public boolean isSettable() {
        return setButton.isEnabled();
    }

    public TileChanges changes() {
        final String typeLabel = (String) typeCombo.getSelectedItem();
        final String description = descriptionField.getText().trim();
        return new TileChanges(
            NO_TYPE_LABEL.equals(typeLabel) ? null : fromLabel(typeLabel),
            description.isEmpty() ? null : description,
            processingPanel.getWorkingPixels()
        );
    }

    private void setFormEnabled(final boolean enabled) {
        typeCombo.setEnabled(enabled);
        descriptionField.setEnabled(enabled);
        processingPanel.setEnabled(enabled);
        updateSetButton();
    }

    private void updateSetButton() {
        setButton.setEnabled(typeCombo.isEnabled() && !NO_TYPE_LABEL.equals(typeCombo.getSelectedItem()));
    }

    private void layOut() {
        setLayout(new GridBagLayout());
        setBorder(createTitledBorder(createEtchedBorder(), "Tile Properties", LEADING, TOP));

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

        add(new JLabel("ID:"), label);
        idValueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        add(idValueLabel, field);

        label.gridy++;
        field.gridy++;
        add(new JLabel("Type:"), label);
        add(typeCombo, field);

        label.gridy++;
        field.gridy++;
        add(new JLabel("Description:"), label);
        add(descriptionField, field);

        label.gridy++;
        label.gridwidth = 2;
        label.anchor = GridBagConstraints.CENTER;
        label.fill = NONE;
        label.insets = new Insets(10, 8, 5, 8);
        final JPanel processingWrapper = new JPanel(new BorderLayout());
        processingWrapper.setBorder(createTitledBorder(createEtchedBorder(), "Tile Processing", LEADING, TOP));
        processingWrapper.add(processingPanel, CENTER);
        add(processingWrapper, label);

        label.gridy++;
        label.insets = new Insets(12, 8, 5, 8);
        setButton.setPreferredSize(new Dimension(120, 28));
        add(setButton, label);

        label.gridy++;
        label.fill = BOTH;
        label.weighty = 1.0;
        add(Box.createGlue(), label);
    }
}
