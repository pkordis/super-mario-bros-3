package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.floorDiv;

public interface PixelAreaCapabilities {
    LevelSceneDimensions getLevelSceneDimensions();
    LevelSceneEditorGrid getLevelSceneEditorGrid();
    LevelSceneLayer getActiveLayer();

    /** The pixel boxes of the enemies, other than {@code ignored}, whose box reaches into {@code area}. */
    List<PixelArea> enemiesOverlapping(PixelArea area, EnemyPlacement ignored);

    default boolean refusesOverlap(final PixelArea area) {
        return refusesOverlap(area, null);
    }

    /**
     * Refuses a placement over {@code area} when it overlaps an object of the active layer other than
     * {@code ignored}, and has the blocking objects blink. Only the NPC and the interactive objects layers hold
     * objects; see kb/editor.md, "Overlapping objects are refused".
     */
    default boolean refusesOverlap(
        final PixelArea area,
        final EnemyPlacement ignored
    ) {
        final List<PixelArea> blockers = objectsOverlapping(area, ignored);
        if (blockers.isEmpty()) {
            return false;
        }
        getLevelSceneEditorGrid().refusePlacement(blockers);
        return true;
    }

    private List<PixelArea> objectsOverlapping(
        final PixelArea area,
        final EnemyPlacement ignored
    ) {
        final LevelSceneLayer activeLayer = getActiveLayer();
        return switch (activeLayer.getType()) {
            case NON_PLAYABLE_CHARACTERS -> enemiesOverlapping(area, ignored);
            case INTERACTIVE_OBJECTS -> interactiveObjectsIn(area);
            case AIR, DECORATIONS_AIR, DECORATIONS_LAND, STATIC_ENVIRONMENT -> List.of();
        };
    }

    private List<PixelArea> interactiveObjectsIn(final PixelArea area) {
        final MultiTileArea cells = new MultiTileArea(
            floorDiv(area.x(), TILE_SPRITE_SIZE),
            floorDiv(area.y(), TILE_SPRITE_SIZE),
            floorDiv(area.lastX(), TILE_SPRITE_SIZE) - floorDiv(area.x(), TILE_SPRITE_SIZE) + 1,
            floorDiv(area.lastY(), TILE_SPRITE_SIZE) - floorDiv(area.y(), TILE_SPRITE_SIZE) + 1
        );
        final Optional<MultiTileArea> inScene = cells.clippedTo(getLevelSceneDimensions());
        if (inScene.isEmpty()) {
            return List.of();
        }
        final List<PixelArea> objects = new ArrayList<>();
        final Tile[][] tiles = getActiveLayer().getTiles();
        for (int row = inScene.get().row(); row <= inScene.get().lastRow(); row++) {
            for (int column = inScene.get().column(); column <= inScene.get().lastColumn(); column++) {
                final Tile tile = tiles[row][column];
                if (tile != null && tile.getType() != null && tile.getType().isInteractiveObject()) {
                    objects.add(new MultiTileArea(of(column, row)).toPixelArea());
                }
            }
        }
        return objects;
    }
}
