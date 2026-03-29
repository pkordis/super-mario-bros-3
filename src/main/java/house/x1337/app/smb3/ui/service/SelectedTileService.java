package house.x1337.app.smb3.ui.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton;
import lombok.Getter;
import lombok.Setter;

@Singleton
public class SelectedTileService {
    @Setter
    @Getter
    private TileButton selectedTileButton = null;

    public Tile getSelectedTile() {
        return getSelectedTileButton() == null ? null : getSelectedTileButton().getTile();
    }
}
