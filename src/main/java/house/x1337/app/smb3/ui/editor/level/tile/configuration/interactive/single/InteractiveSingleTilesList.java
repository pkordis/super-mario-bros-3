package house.x1337.app.smb3.ui.editor.level.tile.configuration.interactive.single;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ui.tile.TileCapabilities.THUMB_SIZE;

@Prototype
public final class InteractiveSingleTilesList extends JList<Tile> {
    private final InteractiveSingleTileCellRenderer renderer = getBean(InteractiveSingleTileCellRenderer.class);
    private final EditInteractiveSingleTiledWindow window;

    public InteractiveSingleTilesList(
        final EditInteractiveSingleTiledWindow window,
        final List<Tile> tiles
    ) {
        final DefaultListModel<Tile> listModel = new DefaultListModel<>();
        tiles.forEach(listModel::addElement);
        super(listModel);
        this.window = window;
    }

    @PostConstruct
    void init() {
        setCellRenderer(renderer);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFixedCellHeight(THUMB_SIZE + 8);
        addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                window.populateForm(getSelectedValue());
            }
        });
    }

    @Override
    public DefaultListModel<Tile> getModel() {
        return (DefaultListModel<Tile>) super.getModel();
    }
}
