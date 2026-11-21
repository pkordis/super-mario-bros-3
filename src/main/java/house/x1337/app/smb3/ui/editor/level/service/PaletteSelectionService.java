package house.x1337.app.smb3.ui.editor.level.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyButton;
import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton;
import house.x1337.app.smb3.ui.editor.level.virtualobject.palette.VirtualObjectButton;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class PaletteSelectionService {
    private final List<Runnable> selectionListeners = new CopyOnWriteArrayList<>();

    @Getter
    private LayerGatedToggleButton selectedButton = null;

    public void select(final LayerGatedToggleButton button) {
        release();
        selectedButton = button;
        button.markSelected(true);
        selectionListeners.forEach(Runnable::run);
    }

    public void clearSelection() {
        if (release()) {
            selectionListeners.forEach(Runnable::run);
        }
    }

    public void addSelectionListener(final Runnable listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionListener(final Runnable listener) {
        selectionListeners.remove(listener);
    }

    private boolean release() {
        if (selectedButton == null) {
            return false;
        }
        selectedButton.markSelected(false);
        selectedButton = null;
        return true;
    }

    public Tile getSelectedTile() {
        return selectedButton instanceof TileButton tileButton ? tileButton.getTile() : null;
    }

    public EnemyStamp getSelectedEnemy() {
        return selectedButton instanceof EnemyButton enemyButton ? enemyButton.getEnemy() : null;
    }

    public VirtualObjectType getSelectedVirtualObject() {
        return selectedButton instanceof VirtualObjectButton virtualObjectButton
            ? virtualObjectButton.getVirtualObjectType()
            : null;
    }

    public boolean hasSelection() {
        return selectedButton != null;
    }
}
