package house.x1337.app.smb3.ui.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.Enemy;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyButton;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton;
import lombok.Getter;

/**
 * Dictates what the next click on the level grid will place.
 *
 * <p>There are two kinds of selection, and they are <b>mutually exclusive</b>: a single tile from the
 * Tiles palette, or a whole enemy from the Enemies palette. Selecting one clears the other — the grid
 * has to know unambiguously whether a click paints one cell or stamps an enemy's whole grid of parts.
 * Both palettes route their clicks through here rather than tracking their own selection, so that
 * exclusivity has exactly one owner.
 */
@Singleton
public class SelectedTileService {
    @Getter
    private TileButton selectedTileButton = null;

    @Getter
    private EnemyButton selectedEnemyButton = null;

    public void select(final TileButton button) {
        clearSelection();
        selectedTileButton = button;
        button.markSelected(true);
    }

    public void select(final EnemyButton button) {
        clearSelection();
        selectedEnemyButton = button;
        button.markSelected(true);
    }

    /** Drops whichever selection is active, leaving the grid with nothing to place. */
    public void clearSelection() {
        if (selectedTileButton != null) {
            selectedTileButton.markSelected(false);
            selectedTileButton = null;
        }
        if (selectedEnemyButton != null) {
            selectedEnemyButton.markSelected(false);
            selectedEnemyButton = null;
        }
    }

    public Tile getSelectedTile() {
        return selectedTileButton == null ? null : selectedTileButton.getTile();
    }

    public Enemy getSelectedEnemy() {
        return selectedEnemyButton == null ? null : selectedEnemyButton.getEnemy();
    }

    public boolean hasSelection() {
        return selectedTileButton != null || selectedEnemyButton != null;
    }
}
