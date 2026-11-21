package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.model.game.Coordinates.of;

public interface MultiTileAreaCapabilities {
    EnemyStampService getEnemyStampService();
    LevelSceneDimensions getLevelSceneDimensions();
    LevelSceneEditorGrid getLevelSceneEditorGrid();
    LevelSceneLayer getActiveLayer();

    /**
     * Refuses a placement over {@code area} when it overlaps an object of the active layer, and has the blocking
     * objects blink. Only the NPC and the interactive objects layers hold objects; see kb/editor.md, "Overlapping
     * objects are refused".
     */
    default boolean refusesOverlap(final MultiTileArea area) {
        final List<MultiTileArea> blockers = objectsOverlapping(area);
        if (blockers.isEmpty()) {
            return false;
        }
        getLevelSceneEditorGrid().refusePlacement(blockers);
        return true;
    }

    private List<MultiTileArea> objectsOverlapping(final MultiTileArea area) {
        final LevelSceneLayer activeLayer = getActiveLayer();
        return switch (activeLayer.getType()) {
            case NON_PLAYABLE_CHARACTERS -> getEnemyStampService()
                .findPlacementsOverlapping(activeLayer.getTiles(), area)
                .stream()
                .map(EnemyPlacement::bounds)
                .toList();
            case INTERACTIVE_OBJECTS -> interactiveObjectsIn(area);
            case AIR, DECORATIONS_AIR, DECORATIONS_LAND, STATIC_ENVIRONMENT -> List.of();
        };
    }

    private List<MultiTileArea> interactiveObjectsIn(final MultiTileArea area) {
        final Optional<MultiTileArea> inScene = area.clippedTo(getLevelSceneDimensions());
        if (inScene.isEmpty()) {
            return List.of();
        }
        final List<MultiTileArea> objects = new ArrayList<>();
        final Tile[][] tiles = getActiveLayer().getTiles();
        for (int row = inScene.get().row(); row <= inScene.get().lastRow(); row++) {
            for (int column = inScene.get().column(); column <= inScene.get().lastColumn(); column++) {
                final Tile tile = tiles[row][column];
                if (tile != null && tile.getType() != null && tile.getType().isInteractiveObject()) {
                    objects.add(new MultiTileArea(of(column, row)));
                }
            }
        }
        return objects;
    }
}
