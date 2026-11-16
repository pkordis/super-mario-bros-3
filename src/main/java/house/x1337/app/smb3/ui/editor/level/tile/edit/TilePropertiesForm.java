package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static house.x1337.app.smb3.enumeration.TileType.fromLabel;
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
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.border.TitledBorder.LEADING;
import static javax.swing.border.TitledBorder.TOP;

/**
 * The tile form shared by the tile windows: the tile's id and pixels, then two sections a radio button switches
 * between. Tile Properties sets the tile's type and description and clears any link; Associate with Existing Level
 * Object links the tile to a level object (see {@link LevelObjectAssociationPanel}). A linked tile opens on the
 * second section.
 */
@Prototype
@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public class TilePropertiesForm extends JPanel {
    static final String NO_TYPE_LABEL = "- (none)";

    private final TileProcessingPanel processingPanel;
    private final LevelObjectAssociationPanel associationPanel;
    private final TileEditing tileEditing;
    private final JLabel idValueLabel = new JLabel();
    private final JRadioButton propertiesRadio = new JRadioButton("Tile properties");
    private final JComboBox<String> typeCombo = new JComboBox<>();
    private final JTextField descriptionField = new JTextField(22);
    private final JButton setButton = new JButton("Set");
    private boolean showingTile;

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
        final ButtonGroup sections = new ButtonGroup();
        sections.add(propertiesRadio);
        sections.add(associationPanel.getRadio());
        propertiesRadio.addActionListener(event -> updateSections());
        associationPanel.getRadio().addActionListener(event -> updateSections());
        layOut();
        load(null);
    }

    /** Shows the tile, or clears and disables the form when {@code tile} is {@code null}. */
    public void load(final Tile tile) {
        showingTile = tile != null;
        associationPanel.load(tile);
        if (tile == null) {
            idValueLabel.setText("");
            idValueLabel.setToolTipText(null);
            typeCombo.setSelectedItem(NO_TYPE_LABEL);
            descriptionField.setText("");
            processingPanel.loadTile(null);
            propertiesRadio.setSelected(true);
            updateSections();
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
        (tile.getLevelObjectId() == null ? propertiesRadio : associationPanel.getRadio()).setSelected(true);
        updateSections();
    }

    /** Runs {@code action} on Set and on Save. */
    public void onSet(final Runnable action) {
        setButton.addActionListener(event -> action.run());
        associationPanel.onSave(action);
    }

    public boolean isSettable() {
        return setButton.isEnabled();
    }

    public boolean isAssociating() {
        return associationPanel.getRadio().isSelected();
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

    /**
     * Saves {@code tile} from the active section.
     *
     * @return {@code false} when nothing was saved
     */
    public boolean save(final Component parent, final Tile tile) {
        if (!isAssociating()) {
            return tileEditing.save(parent, tile, changes());
        }
        if (associationPanel.warnAboutDuplicateKeys(parent)) {
            return false;
        }
        return tileEditing.associate(parent, tile, processingPanel.getWorkingPixels(), associationPanel.association());
    }

    private void updateSections() {
        final boolean properties = showingTile && propertiesRadio.isSelected();
        propertiesRadio.setEnabled(showingTile);
        associationPanel.getRadio().setEnabled(showingTile);
        typeCombo.setEnabled(properties);
        descriptionField.setEnabled(properties);
        processingPanel.setEnabled(showingTile);
        associationPanel.setActive(showingTile && isAssociating());
        updateSetButton();
    }

    private void updateSetButton() {
        setButton.setEnabled(typeCombo.isEnabled() && !NO_TYPE_LABEL.equals(typeCombo.getSelectedItem()));
    }

    private void layOut() {
        setLayout(new GridBagLayout());
        setBorder(createTitledBorder(createEtchedBorder(), "Tile", LEADING, TOP));

        final GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = 0;
        cell.gridwidth = 3;
        cell.anchor = WEST;
        cell.insets = new Insets(5, 8, 5, 8);
        final JPanel idRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        idRow.add(new JLabel("ID:  "));
        idValueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        idRow.add(idValueLabel);
        add(idRow, cell);

        cell.gridy++;
        cell.anchor = GridBagConstraints.CENTER;
        cell.fill = NONE;
        final JPanel processingWrapper = new JPanel(new BorderLayout());
        processingWrapper.setBorder(createTitledBorder(createEtchedBorder(), "Tile Processing", LEADING, TOP));
        processingWrapper.add(processingPanel, CENTER);
        add(processingWrapper, cell);

        // The sections take all the height left, so the association's data table can grow
        cell.gridy++;
        cell.gridwidth = 1;
        cell.anchor = GridBagConstraints.NORTH;
        cell.fill = BOTH;
        cell.weightx = 0.5;
        cell.weighty = 1.0;
        add(buildPropertiesSection(), cell);

        cell.gridx++;
        cell.fill = NONE;
        cell.weightx = 0;
        cell.anchor = GridBagConstraints.CENTER;
        final JLabel orLabel = new JLabel("or");
        orLabel.setFont(orLabel.getFont().deriveFont(BOLD));
        add(orLabel, cell);

        cell.gridx++;
        cell.fill = BOTH;
        cell.weightx = 0.5;
        cell.anchor = GridBagConstraints.NORTH;
        add(associationPanel, cell);
    }

    private JPanel buildPropertiesSection() {
        final JPanel section = new JPanel(new BorderLayout(4, 4));
        section.setBorder(createCompoundBorder(createEtchedBorder(), createEmptyBorder(4, 4, 4, 4)));
        propertiesRadio.setFont(propertiesRadio.getFont().deriveFont(BOLD));
        section.add(propertiesRadio, NORTH);

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

        fields.add(new JLabel("Type:"), label);
        fields.add(typeCombo, field);

        label.gridy++;
        field.gridy++;
        fields.add(new JLabel("Description:"), label);
        fields.add(descriptionField, field);

        label.gridy++;
        label.weighty = 1.0;
        fields.add(Box.createGlue(), label);
        section.add(fields, CENTER);

        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        setButton.setPreferredSize(new Dimension(120, 28));
        footer.add(setButton);
        section.add(footer, SOUTH);
        return section;
    }
}
