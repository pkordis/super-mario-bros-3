package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.service.TileMoveSummary;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import lombok.RequiredArgsConstructor;

import java.awt.Component;
import java.util.List;

/**
 * Saves a tile edited in either tile window and brings everything that shows it up to date: the tile store, the
 * Tiles palette and every open level scene tab. A type whose layer differs from the old one also moves the tile to
 * its new layer in every stored scene and every open tab, after the author confirms. See {@code kb/editor.md},
 * "Editing tiles".
 */
@Singleton
@RequiredArgsConstructor
public class TileEditing {
    private final TileService tileService;
    private final LevelSceneService levelSceneService;
    private final LevelSceneTabs levelSceneTabs;
    private final TilePalettePanel tilePalettePanel;
    private final TileWindows tileWindows;

    /**
     * @param parent  the window the confirmation is centred on
     * @param tile    the tile to change
     * @param changes what the form holds for it
     * @return {@code false} when the author cancelled the layer move, in which case nothing changed
     */
    public boolean save(final Component parent, final Tile tile, final TileChanges changes) {
        final LevelSceneLayerType from = owningLayerOf(tile.getType());
        final LevelSceneLayerType to = owningLayerOf(changes.type());
        final boolean moves = from != null && to != null && from != to;
        if (moves && !confirmMove(parent, tile.getId(), from, to)) {
            return false;
        }
        changes.applyTo(tile);
        tileService.updateTile(tile);
        final List<LevelSceneEditorTab> tabs = levelSceneTabs.getLevelSceneTabs();
        if (moves) {
            levelSceneService.moveTile(tile.getId(), to);
            tabs.forEach(tab -> tab.moveTile(tile, to));
        }
        tilePalettePanel.showTile(tile);
        for (final LevelSceneEditorTab tab : tabs) {
            tab.refreshTile(tile);
            // Re-routing rebuilds a tab's layers from its import grid, so only the tabs whose import holds the tile
            if (tab.importsTile(tile.getId())) {
                tab.reRouteImportedLayers();
            }
        }
        return true;
    }

    String layerMoveMessage(
        final LevelSceneLayerType from,
        final LevelSceneLayerType to,
        final TileMoveSummary stored,
        final int openCells
    ) {
        return "<html>The new type moves this tile from the <b>" + from.getLabel() + "</b> layer to the <b>"
            + to.getLabel() + "</b> layer:<br>"
            + stored.cells() + " cell(s) in " + stored.scenes() + " saved scene(s), and " + openCells
            + " cell(s) in the open tabs.<br>"
            + "A cell of the new layer that already holds a tile gets this one instead.<br><br>"
            + "Move it?</html>";
    }

    private boolean confirmMove(
        final Component parent,
        final int tileId,
        final LevelSceneLayerType from,
        final LevelSceneLayerType to
    ) {
        final TileMoveSummary stored = levelSceneService.countTileCellsOutside(tileId, to);
        final int openCells = levelSceneTabs.getLevelSceneTabs().stream()
            .mapToInt(tab -> tab.countTileCellsOutside(tileId, to))
            .sum();
        if (stored.cells() == 0 && openCells == 0) {
            return true;
        }
        return tileWindows.confirmLayerMove(parent, layerMoveMessage(from, to, stored, openCells));
    }

    private LevelSceneLayerType owningLayerOf(final TileType type) {
        return type == null ? null : type.getLevelSceneLayerOwningType();
    }
}
