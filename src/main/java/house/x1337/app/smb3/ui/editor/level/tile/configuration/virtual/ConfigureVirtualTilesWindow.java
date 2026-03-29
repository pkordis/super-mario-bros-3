package house.x1337.app.smb3.ui.editor.level.tile.configuration.virtual;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.scene.LevelSceneEditorWindow;
import jakarta.annotation.PostConstruct;
import lombok.Getter;

import javax.swing.JDialog;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Prototype
public final class ConfigureVirtualTilesWindow extends JDialog implements ComponentsBuilder {
    @Getter
    private final List<Tile> virtualTiles;
    @Getter(lazy = true)
    private final VirtualTilePreviewPanel previewPanel = getBean(VirtualTilePreviewPanel.class);
    private final TileService tileService;

    public ConfigureVirtualTilesWindow(
        final LevelSceneEditorWindow parent,
        final TileService tileService
    ) {
        super(parent);
        this.tileService = tileService;
        this.virtualTiles = new ArrayList<>(tileService.getVirtualTiles());
    }

    @PostConstruct
    void init() {
        setTitle("Configure Virtual Tiles");
        setModal(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(true);
        setContentPane(buildContent());
        setMinimumSize(new Dimension(640, 400));
        pack();
        setLocationRelativeTo(getParent());
    }

    @Override
    public void onTileUpdated(final Tile tile) {
        tileService.updateTile(tile);
    }
}
