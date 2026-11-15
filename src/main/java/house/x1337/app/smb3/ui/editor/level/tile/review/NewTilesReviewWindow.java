package house.x1337.app.smb3.ui.editor.level.tile.review;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import house.x1337.app.smb3.ui.editor.level.tile.edit.TileEditing;
import house.x1337.app.smb3.ui.editor.level.tile.edit.TilePropertiesForm;
import jakarta.annotation.PostConstruct;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * Lists tiles that still need a type, beside the tile form. Each tile set leaves the list, and the window closes
 * once the list is empty. Opened after a PNG import and by Tiles &gt; Handle Unresolved.
 */
@Prototype
public final class NewTilesReviewWindow extends JDialog implements ComponentsBuilder {
    private final TilePropertiesForm form = getBean(TilePropertiesForm.class);
    private final TileEditing tileEditing = getBean(TileEditing.class);
    private final NewTilesList tileList;
    private final JLabel headerLabel = new JLabel();
    private Tile selectedTile;

    public NewTilesReviewWindow(final GameEditorWindow parent, final List<Tile> tiles) {
        super(parent);
        this.tileList = getBean(NewTilesList.class, this, tiles);
    }

    @PostConstruct
    void init() {
        setTitle("New Tiles - Review Required");
        setModal(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(true);
        setContentPane(buildContent(tileList, headerLabel, form));
        form.onSet(this::saveSelectedTile);
        setMinimumSize(new Dimension(720, 680));
        pack();
        setLocationRelativeTo(getParent());
        updateHeader();
    }

    public void populateForm(final Tile tile) {
        selectedTile = tile;
        form.load(tile);
    }

    private void saveSelectedTile() {
        if (selectedTile == null || !tileEditing.save(this, selectedTile, form.changes())) {
            return;
        }
        final DefaultListModel<Tile> listModel = tileList.getModel();
        final int index = listModel.indexOf(selectedTile);
        if (index >= 0) {
            listModel.remove(index);
        }
        if (listModel.isEmpty()) {
            populateForm(null);
            updateHeader();
            setVisible(false);
            dispose();
            return;
        }
        tileList.setSelectedIndex(Math.min(index, listModel.size() - 1));
        updateHeader();
    }

    private void updateHeader() {
        headerLabel.setText(tileList.getModel().size() + " tile(s) still need a type");
    }
}
