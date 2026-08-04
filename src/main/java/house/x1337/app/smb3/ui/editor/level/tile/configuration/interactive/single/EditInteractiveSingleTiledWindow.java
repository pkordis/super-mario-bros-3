package house.x1337.app.smb3.ui.editor.level.tile.configuration.interactive.single;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.LevelSceneEditorWindow;
import jakarta.annotation.PostConstruct;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.util.stream.Collectors.toList;

@Prototype
public final class EditInteractiveSingleTiledWindow extends JDialog implements ComponentsBuilder {
    private final LevelObjectService levelObjectService = getBean(LevelObjectService.class);
    private final InteractiveSingleTilesList tileList;

    // Form fields
    private final JTextField descriptionField = new JTextField(22);
    private final JComboBox<String> objectTypeCombo = buildObjectTypeCombo();
    private final JButton saveButton = new JButton("Save");

    private final JLabel headerLabel = new JLabel();
    private Tile selectedTile;

    public EditInteractiveSingleTiledWindow(final LevelSceneEditorWindow parent) {
        super(parent);
        final List<Tile> allInteractiveSingle = getBean(TileService.class).getInteractiveSingleTiles();
        final Set<Integer> unclassifiedIds = levelObjectService.getUnclassifiedIds(
            allInteractiveSingle.stream().map(Tile::getId).collect(toList())
        );
        final List<Tile> unclassified = allInteractiveSingle
            .stream()
            .filter(t -> unclassifiedIds.contains(t.getId()))
            .collect(toList());
        this.tileList = getBean(InteractiveSingleTilesList.class, this, unclassified);
    }

    @PostConstruct
    void init() {
        setTitle("Edit Interactive Objects - Single Tiled");
        setModal(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(true);
        setContentPane(
            buildContent(
                tileList,
                headerLabel,
                descriptionField,
                objectTypeCombo,
                saveButton
            )
        );
        setMinimumSize(new Dimension(560, 480));
        pack();
        setLocationRelativeTo(getParent());
        updateHeader();
    }

    public void populateForm(final Tile tile) {
        selectedTile = tile;
        if (tile == null) {
            descriptionField.setText("");
            objectTypeCombo.setSelectedItem(NO_OBJECT_TYPE_LABEL);
            setFormEnabled(false);
            return;
        }
        setFormEnabled(true);

        final Optional<LevelObjectRecord> existing = levelObjectService.findByTileId(tile.getId());
        descriptionField.setText(existing.map(LevelObjectRecord::getDescription).orElse(""));

        final String storedType = existing.map(LevelObjectRecord::getType).orElse(null);
        objectTypeCombo.setSelectedItem(
            storedType != null ? labelForName(storedType) : NO_OBJECT_TYPE_LABEL
        );

        updateSaveButtonState(objectTypeCombo, saveButton);
    }

    @Override
    public void saveSelectedTile() {
        if (selectedTile == null) {
            return;
        }

        final String selectedLabel = (String) objectTypeCombo.getSelectedItem();
        final String typeName = NO_OBJECT_TYPE_LABEL.equals(selectedLabel)
            ? null
            : nameForLabel(selectedLabel);

        final String rawDesc = descriptionField.getText().trim();

        final LevelObjectRecord record = LevelObjectRecord.builder()
            .id(selectedTile.getId())
            .type(typeName)
            .description(rawDesc.isEmpty() ? null : rawDesc)
            .build();

        levelObjectService.upsert(record);

        // Remove from the pending list and advance selection
        final DefaultListModel<Tile> model = tileList.getModel();
        final int idx = model.indexOf(selectedTile);
        if (idx >= 0) {
            model.remove(idx);
        }

        if (model.isEmpty()) {
            populateForm(null);
        } else {
            tileList.setSelectedIndex(Math.min(idx, model.size() - 1));
        }

        updateHeader();
    }

    void setFormEnabled(final boolean enabled) {
        descriptionField.setEnabled(enabled);
        objectTypeCombo.setEnabled(enabled);
        updateSaveButtonState(objectTypeCombo, saveButton);
    }

    private void updateHeader() {
        headerLabel.setText(tileList.getModel().size() + " tile(s) without an object type");
    }

    private static String nameForLabel(final String label) {
        for (final LevelObjectTypeSingleTiled value : LevelObjectTypeSingleTiled.values()) {
            if (value.getLabel().equals(label)) {
                return value.name();
            }
        }
        return null;
    }

    private static String labelForName(final String name) {
        for (final LevelObjectTypeSingleTiled value : LevelObjectTypeSingleTiled.values()) {
            if (value.name().equals(name)) {
                return value.getLabel();
            }
        }
        return NO_OBJECT_TYPE_LABEL;
    }
}
