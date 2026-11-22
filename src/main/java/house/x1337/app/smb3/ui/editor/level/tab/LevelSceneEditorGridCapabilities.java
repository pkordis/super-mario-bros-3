package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.model.ui.tile.Tile;

public interface LevelSceneEditorGridCapabilities {
    LevelSceneEditorGrid getLevelSceneEditorGrid();
    Tile[][] getImportGrid();
    void setImportGrid(Tile[][] importGrid);
}
