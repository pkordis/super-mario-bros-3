package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;

public interface EnemyStampCapabilities extends MultiTileAreaCapabilities {
    EnemyLevelSceneGridStamper getEnemyLevelSceneGridStamper();
    LevelSceneEditorGrid getLevelSceneEditorGrid();

    default boolean isNpcLayerActive() {
        final LevelSceneLayer activeLayer = getActiveLayer();
        return activeLayer != null && activeLayer.getType() == NON_PLAYABLE_CHARACTERS;
    }

    default boolean removeEnemyAt(final Coordinates cell) {
        if (!isNpcLayerActive()) {
            return false;
        }
        final Tile[][] tiles = getActiveLayer().getTiles();
        final Optional<EnemyPlacement> enemy = getEnemyStampService().findPlacementHolding(tiles, cell);
        enemy.ifPresent(placement -> placement
            .heldCells(tiles)
            .forEach(part -> tiles[part.y()][part.x()] = NULL_TILE));
        return enemy.isPresent();
    }

    /**
     * Stamps a whole enemy onto the NPC layer, the clicked cell taking the enemy's rendering starter (see
     * {@link EnemyLevelSceneGridStamper}). Refuses to write anywhere but the NPC layer — the palette only arms
     * an enemy once that layer is active (switching to it on the click if need be) and releases it when the
     * layer moves away, and this keeps that true even if an enemy is armed by some other path.
     */
    default void stampEnemy(final EnemyStamp enemy, final Coordinates cell) {
        final LevelSceneLayer activeLayer = getActiveLayer();
        final LevelSceneEditorGrid levelSceneEditorGrid = getLevelSceneEditorGrid();
        if (activeLayer == null || activeLayer.getType() != NON_PLAYABLE_CHARACTERS || !enemy.isWellFormed()) {
            return;
        }
        final MultiTileArea placed = enemy.placedAt(cell).bounds();
        if (refusesOverlap(placed)) {
            return;
        }
        getEnemyLevelSceneGridStamper().stamp(activeLayer.getTiles(), enemy, cell);
        levelSceneEditorGrid.notePlacement(placed);
        levelSceneEditorGrid.repaint();
    }
}
