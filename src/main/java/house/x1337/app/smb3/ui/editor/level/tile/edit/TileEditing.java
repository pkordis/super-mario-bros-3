package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.service.TileMoveSummary;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Copy;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Link;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Unlink;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import lombok.RequiredArgsConstructor;

import java.awt.Component;
import java.util.List;

import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;

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
    private final LevelObjectService levelObjectService;
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
        return save(parent, tile, changes, () -> { });
    }

    /**
     * Links {@code tile} to a level object, to a new one made from a changed level object, or to none. A linked tile
     * takes the type of the level object's own tile and keeps its own description.
     *
     * @param argbData the tile's pixels, {@code null} to keep its own
     * @return {@code false} when the author cancelled, in which case nothing changed
     */
    public boolean associate(
        final Component parent,
        final Tile tile,
        final int[] argbData,
        final LevelObjectAssociation association
    ) {
        return switch (association) {
            case Unlink _ -> save(parent, tile, new TileChanges(tile.getType(), tile.getDescription(), argbData));
            case Link link -> save(parent, tile, new TileChanges(
                linkedTileType(link.target()),
                tile.getDescription(),
                argbData,
                link.target().getId()
            ));
            case Copy copy -> confirmReplacement(parent, tile) && save(
                parent,
                tile,
                new TileChanges(linkedTileType(copy.source()), tile.getDescription(), argbData, tile.getId()),
                () -> levelObjectService.upsert(copy.toRecord(tile.getId()))
            );
        };
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

    String replacementMessage(final long otherTiles) {
        return "<html>" + otherTiles + " other tile(s) are linked to this tile's level object.<br>"
            + "The new level object replaces that one for them too.<br><br>"
            + "Save it?</html>";
    }

    private boolean save(
        final Component parent,
        final Tile tile,
        final TileChanges changes,
        final Runnable onConfirmed
    ) {
        final LevelSceneLayerType from = owningLayerOf(tile.getType());
        final LevelSceneLayerType to = owningLayerOf(changes.type());
        final boolean moves = from != null && to != null && from != to;
        if (moves && !confirmMove(parent, tile.getId(), from, to)) {
            return false;
        }
        onConfirmed.run();
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

    private TileType linkedTileType(final LevelObjectRecord levelObject) {
        return tileService.findById(levelObject.getId())
            .map(Tile::getType)
            .orElse(OBJECT_INTERACTIVE_SINGLE);
    }

    private boolean confirmReplacement(final Component parent, final Tile tile) {
        final long otherTiles = tileService.getTilesLinkedTo(tile.getId())
            .stream()
            .filter(other -> other.getId() != tile.getId())
            .count();
        return otherTiles == 0 || tileWindows.confirmLevelObjectReplacement(parent, replacementMessage(otherTiles));
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
