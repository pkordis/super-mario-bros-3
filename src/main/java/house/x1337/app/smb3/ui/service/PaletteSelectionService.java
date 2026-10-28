package house.x1337.app.smb3.ui.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyButton;
import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton;
import lombok.Getter;

@Singleton
public class PaletteSelectionService {
    @Getter
    private LayerGatedToggleButton selectedButton = null;

    public void select(final LayerGatedToggleButton button) {
        clearSelection();
        selectedButton = button;
        button.markSelected(true);
    }

    public void clearSelection() {
        if (selectedButton != null) {
            selectedButton.markSelected(false);
            selectedButton = null;
        }
    }

    public Tile getSelectedTile() {
        return selectedButton instanceof TileButton tileButton ? tileButton.getTile() : null;
    }

    public EnemyStamp getSelectedEnemy() {
        return selectedButton instanceof EnemyButton enemyButton ? enemyButton.getEnemy() : null;
    }

    public boolean hasSelection() {
        return selectedButton != null;
    }
}
