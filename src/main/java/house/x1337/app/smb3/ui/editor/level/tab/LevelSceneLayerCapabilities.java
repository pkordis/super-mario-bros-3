package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;

public interface LevelSceneLayerCapabilities extends LevelSceneEditorGridCapabilities, EnemyStampCapabilities {
    LevelSceneLayer getActiveLayer();
    List<LevelSceneLayer> getLayers();
    LevelSceneImportRouter getLevelSceneImportRouter();
    void setActiveLayer(LevelSceneLayer layer);
    void setLayers(List<LevelSceneLayer> layers);

    /**
     * Re-routes the retained import grid into the layers. Called as the user classifies tiles so newly
     * typed tiles move to their owning layer and the AIR auto-fill is recomputed. No-op for scenes not
     * built from an import.
     */
    default void reRouteImportedLayers() {
        final Tile[][] importGrid = getImportGrid();
        if (importGrid == null) {
            return;
        }
        final Map<LevelSceneLayerType, Boolean> visibilityByType = new EnumMap<>(LevelSceneLayerType.class);
        final List<LevelSceneLayer> layers = getLayers();
        final LevelSceneLayer activeLayer = getActiveLayer();
        if (layers != null) {
            for (final LevelSceneLayer layer : layers) {
                visibilityByType.put(layer.getType(), layer.isVisible());
            }
        }
        final LevelSceneLayerType activeType = (activeLayer != null) ? activeLayer.getType() : AIR;

        final List<EnemyPlacement> enemies = new ArrayList<>(findEnemyPlacements());
        final List<LevelSceneLayer> routed = getLevelSceneImportRouter().route(importGrid, getLevelSceneDimensions());
        for (final LevelSceneLayer layer : routed) {
            final Boolean visible = visibilityByType.get(layer.getType());
            if (visible != null) {
                layer.setVisible(visible);
            }
            if (layer.getType() == NON_PLAYABLE_CHARACTERS) {
                layer.setEnemyPlacements(enemies);
            }
        }
        setLayers(routed);
        final LevelSceneLayer levelSceneLayer = routed.stream()
            .filter(layer -> layer.getType() == activeType)
            .findFirst()
            .orElse(routed.getFirst());
        setActiveLayer(levelSceneLayer);
        getLevelSceneEditorGrid().repaint();
    }
}
