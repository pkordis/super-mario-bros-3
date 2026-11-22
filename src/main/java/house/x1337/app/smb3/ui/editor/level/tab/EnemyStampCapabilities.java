package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;

/**
 * The enemies placed in the NPC layer: placing, selecting, nudging and removing them, each by its pixel box. See
 * kb/editor.md, "Enemy placements".
 */
public interface EnemyStampCapabilities extends PixelAreaCapabilities {
    EnemyStampService getEnemyStampService();
    List<LevelSceneLayer> getLayers();
    EnemyPlacement getSelectedEnemyPlacement();
    void setSelectedEnemyPlacement(EnemyPlacement placement);

    default boolean isNpcLayerActive() {
        final LevelSceneLayer activeLayer = getActiveLayer();
        return activeLayer != null && activeLayer.getType() == NON_PLAYABLE_CHARACTERS;
    }

    /** The NPC layer's placements, bottom to top; changing the list changes the scene. */
    default List<EnemyPlacement> findEnemyPlacements() {
        final List<LevelSceneLayer> layers = getLayers();
        if (layers == null) {
            return List.of();
        }
        return layers.stream()
            .filter(layer -> layer.getType() == NON_PLAYABLE_CHARACTERS && layer.getEnemyPlacements() != null)
            .findFirst()
            .map(LevelSceneLayer::getEnemyPlacements)
            .orElse(List.of());
    }

    default Optional<EnemyStamp> findStampOf(final EnemyPlacement placement) {
        return getEnemyStampService().findById(placement.stampId()).filter(EnemyStamp::isWellFormed);
    }

    default Optional<PixelArea> boundsOf(final EnemyPlacement placement) {
        return findStampOf(placement).map(stamp -> stamp.boundsOf(placement));
    }

    @Override
    default List<PixelArea> enemiesOverlapping(final PixelArea area, final EnemyPlacement ignored) {
        return findEnemyPlacements()
            .stream()
            .filter(placement -> !placement.equals(ignored))
            .map(this::boundsOf)
            .flatMap(Optional::stream)
            .filter(bounds -> bounds.intersects(area))
            .toList();
    }

    /** The topmost enemy whose box holds {@code levelPixel}. */
    default Optional<EnemyPlacement> findEnemyAt(final Coordinates levelPixel) {
        final List<EnemyPlacement> placements = findEnemyPlacements();
        for (int index = placements.size() - 1; index >= 0; index--) {
            final EnemyPlacement placement = placements.get(index);
            if (boundsOf(placement).filter(bounds -> bounds.contains(levelPixel)).isPresent()) {
                return Optional.of(placement);
            }
        }
        return Optional.empty();
    }

    /** Selects the enemy under {@code levelPixel} on the active NPC layer, or nothing. */
    default void selectEnemyAt(final Coordinates levelPixel) {
        final EnemyPlacement selected = isNpcLayerActive() ? findEnemyAt(levelPixel).orElse(null) : null;
        if (!Objects.equals(selected, getSelectedEnemyPlacement())) {
            setSelectedEnemyPlacement(selected);
            getLevelSceneEditorGrid().repaint();
        }
    }

    default boolean removeEnemyAt(final Coordinates levelPixel) {
        if (!isNpcLayerActive()) {
            return false;
        }
        final Optional<EnemyPlacement> enemy = findEnemyAt(levelPixel);
        enemy.ifPresent(placement -> {
            findEnemyPlacements().remove(placement);
            if (placement.equals(getSelectedEnemyPlacement())) {
                setSelectedEnemyPlacement(null);
            }
        });
        return enemy.isPresent();
    }

    default void stampEnemy(final EnemyStamp enemy, final Coordinates cell) {
        placeEnemy(enemy, enemy.placedAt(cell));
    }

    default Tile[][] staticEnvironmentTiles() {
        final List<LevelSceneLayer> layers = getLayers();
        if (layers == null) {
            return null;
        }
        return layers.stream()
            .filter(layer -> layer.getType() == STATIC_ENVIRONMENT)
            .findFirst()
            .map(LevelSceneLayer::getTiles)
            .orElse(null);
    }

    /**
     * Places a whole enemy on the NPC layer at {@code placement}'s pixel, and selects it. Refuses a placement whose
     * rendering starter is outside the scene, and to place anywhere but the NPC layer: the palette only arms an enemy
     * once that layer is active, and this keeps that true even if an enemy is armed by some other path. A placement
     * the stamp's own rules refuse has its box blink, and one over another enemy has that enemy blink.
     */
    default void placeEnemy(final EnemyStamp enemy, final EnemyPlacement placement) {
        if (!isNpcLayerActive() || !enemy.isWellFormed() || isOutsideTheScene(placement)) {
            return;
        }
        final PixelArea bounds = enemy.boundsOf(placement);
        if (refusesApplication(enemy, placement, bounds) || refusesOverlap(bounds)) {
            return;
        }
        findEnemyPlacements().add(placement);
        setSelectedEnemyPlacement(placement);
        getLevelSceneEditorGrid().notePlacement(bounds);
        getLevelSceneEditorGrid().repaint();
    }

    /**
     * Moves the selected enemy by ({@code dx}, {@code dy}) level pixels. Refused when its rendering starter would
     * leave the scene, when the stamp's rules refuse the new place, or when its box would overlap another enemy.
     *
     * @return whether it moved
     */
    default boolean nudgeSelectedEnemy(final int dx, final int dy) {
        final EnemyPlacement selected = getSelectedEnemyPlacement();
        final List<EnemyPlacement> placements = findEnemyPlacements();
        final int index = selected == null ? -1 : placements.indexOf(selected);
        if (!isNpcLayerActive() || index < 0) {
            return false;
        }
        final EnemyPlacement moved = selected.movedBy(dx, dy);
        final Optional<EnemyStamp> stamp = findStampOf(moved);
        if (stamp.isEmpty() || isOutsideTheScene(moved)) {
            return false;
        }
        final PixelArea bounds = stamp.get().boundsOf(moved);
        if (refusesApplication(stamp.get(), moved, bounds) || refusesOverlap(bounds, selected)) {
            return false;
        }
        placements.set(index, moved);
        setSelectedEnemyPlacement(moved);
        getLevelSceneEditorGrid().repaint();
        return true;
    }

    private boolean refusesApplication(
        final EnemyStamp enemy,
        final EnemyPlacement placement,
        final PixelArea bounds
    ) {
        if (enemy.validateApplication(staticEnvironmentTiles(), placement)) {
            return false;
        }
        getLevelSceneEditorGrid().refusePlacement(List.of(bounds));
        return true;
    }

    default boolean isOutsideTheScene(final EnemyPlacement placement) {
        final LevelSceneDimensions dimensions = getLevelSceneDimensions();
        return placement.x() < 0 || placement.y() < 0
            || placement.x() >= dimensions.columns() * TILE_SPRITE_SIZE
            || placement.y() >= dimensions.rows() * TILE_SPRITE_SIZE;
    }

    default String enemyPlacementNote(final Coordinates enemyPixel) {
        return enemyPixel == null
            ? "  |  click places its rendering starter"
            : "  |  click places its rendering starter at x=" + enemyPixel.x() + " y=" + enemyPixel.y()
            + " (Ctrl: on the cell)";
    }

    /** The selected enemy's position in level pixels, and how to nudge it; empty while none is selected. */
    default String selectedEnemyNote() {
        final EnemyPlacement selectedEnemyPlacement = getSelectedEnemyPlacement();
        return selectedEnemyPlacement == null
            ? ""
            : "  |  selected enemy at x=" + selectedEnemyPlacement.x() + " y=" + selectedEnemyPlacement.y()
            + " (arrows: 1 px, Shift: 4 px)";
    }
}
