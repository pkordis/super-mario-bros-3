package house.x1337.app.smb3.ui.editor.level.tile.review;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.LevelSceneEditorWindow;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import jakarta.annotation.PostConstruct;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.TileType.fromLabel;

@Prototype
public final class NewTilesReviewWindow extends JDialog implements ComponentsBuilder {
    private final NewTileProcessingPanel newTileProcessingPanel = getBean(NewTileProcessingPanel.class);
    private final TilePalettePanel tilePalettePanel = getBean(TilePalettePanel.class);
    private final TileService tileService = getBean(TileService.class);
    private final LevelSceneEditorTab newTab;
    private final NewTilesList tileList;

    // Form fields
    private final JLabel idValueLabel = new JLabel();
    private final JComboBox<String> typeCombo = buildTypeCombo();
    private final JTextField descriptionField = new JTextField(22);
    private final JButton setButton = new JButton("Set");

    // Header label (updated as tiles are classified)
    private final JLabel headerLabel = new JLabel();
    private Tile selectedTile;

    public NewTilesReviewWindow(
        final LevelSceneEditorWindow parent,
        final LevelSceneEditorTab newTab,
        final List<Tile> tileList
    ) {
        super(parent);
        this.newTab = newTab;
        this.tileList = getBean(NewTilesList.class, this, tileList);
    }

    @PostConstruct
    void init() {
        setTitle("New Tiles – Review Required");
        setModal(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(true);
        setContentPane(
            buildContent(
                tileList,
                headerLabel,
                idValueLabel,
                typeCombo,
                descriptionField,
                setButton,
                newTileProcessingPanel
            )
        );
        setMinimumSize(new Dimension(720, 680));
        pack();
        setLocationRelativeTo(getParent());
        updateHeader();
    }

    public void populateForm(final Tile tile) {
        selectedTile = tile;
        if (tile == null) {
            idValueLabel.setText("");
            typeCombo.setSelectedItem(NO_TYPE_LABEL);
            descriptionField.setText("");
            newTileProcessingPanel.loadTile(null);
            setFormEnabled(false);
            return;
        }
        setFormEnabled(true);

        // ID (display the sha256 hash, truncated; full value in tooltip)
        final String sha256 = tile.getSha256();
        final String id = sha256 != null ? sha256 : String.valueOf(tile.getId());
        idValueLabel.setText(id.length() > 28
            ? id.substring(0, 12) + "..." + id.substring(id.length() - 8)
            : id
        );
        idValueLabel.setToolTipText(id);

        // Type
        typeCombo.setSelectedItem(tile.getType() == null ? NO_TYPE_LABEL : tile.getType().getLabel());

        // Description
        descriptionField.setText(tile.getDescription() != null ? tile.getDescription() : "");

        // Tile processing pane
        newTileProcessingPanel.loadTile(tile);
    }

    @PostConstruct
    public void saveSelectedTile() {
        if (selectedTile == null) {
            return;
        }
        final DefaultListModel<Tile> listModel = tileList.getModel();
        final String typeString = (String) typeCombo.getSelectedItem();
        selectedTile.setType(NO_TYPE_LABEL.equals(typeString) ? null : fromLabel(typeString));

        final String desc = descriptionField.getText().trim();
        selectedTile.setDescription(desc.isEmpty() ? null : desc);

        // Apply pixel edits from the tile processing pane
        final int[] updatedPixels = newTileProcessingPanel.getWorkingPixels();
        if (updatedPixels != null) {
            selectedTile.setArgbData(updatedPixels);
        }

        tileService.updateTile(selectedTile);

        // Forward to the Tiles palette
        tilePalettePanel.addTile(selectedTile);

        // Drop the grid's stale cached image so the classified pixels render immediately
        newTab.refreshTile(selectedTile);

        // Route the now-classified tile (and any classified earlier) into its owning layer; the AIR
        // layer's dominant-tile auto-fill is recomputed as the user goes.
        newTab.reRouteImportedLayers();

        // Remove from list and advance selection
        final int idx = listModel.indexOf(selectedTile);
        if (idx >= 0) {
            listModel.remove(idx);
        }

        if (listModel.isEmpty()) {
            populateForm(null);
            updateHeader();

            // all done - close automatically
            setVisible(false);
            dispose();
        } else {
            final int nextIdx = Math.min(idx, listModel.size() - 1);
            tileList.setSelectedIndex(nextIdx);
            updateHeader();
        }

        newTab.repaint();
    }

    void setFormEnabled(final boolean enabled) {
        typeCombo.setEnabled(enabled);
        descriptionField.setEnabled(enabled);
        newTileProcessingPanel.setEnabled(enabled);
        updateSetButtonState(typeCombo, setButton);
    }

    private String buildHeaderText() {
        final int remaining = tileList.getModel().size();
        return remaining + " tile(s) still need a type";
    }

    private void updateHeader() {
        headerLabel.setText(buildHeaderText());
    }
}
