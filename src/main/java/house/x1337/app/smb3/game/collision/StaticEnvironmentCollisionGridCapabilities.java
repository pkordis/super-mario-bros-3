package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.SolidLevelObject;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.DUMMY_SOLID_OBJECT;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.TileType.Category.COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.ONE_WAY_PLATFORM;
import static house.x1337.app.smb3.enumeration.TileType.NULL;

public interface StaticEnvironmentCollisionGridCapabilities {
    /**
     * Builds the scene's <b>static terrain</b>: the tile-aligned solids and tile-bound interactive
     * objects the player is resolved against.
     *
     * <p>Two things are deliberately outside it. The
     * {@link house.x1337.app.smb3.enumeration.LevelSceneLayerType#NON_PLAYABLE_CHARACTERS} layer is not
     * consolidated at all — it holds actors, not terrain, and letting it take part would also let a
     * character painted over a block cell hide that block from collision. And a cell whose record names
     * a <em>multi-tiled</em> type is left as a plain tile, because such a type is a whole entity rather
     * than a cell: enemies are placed into the active-object world instead (see
     * {@code ActiveObjectGrid#spawnPlacedEnemies}). Nothing here knows what an enemy is.
     */
    default StaticEnvironmentCollisionGrid toCollisionGrid(final GameEngine gameEngine) {
        final LevelScene levelScene = (LevelScene) this;
        final int rows = levelScene.getDimensions().rows();
        final int columns = levelScene.getDimensions().columns();
        final LevelSceneDimensions dimensions = new LevelSceneDimensions(columns, rows);
        final Tile[][] tiles = levelScene.getTilesOfLayersBelow(NON_PLAYABLE_CHARACTERS);

        // What each cell looks like without the interactive-objects layer. Consolidation is lossy - an
        // interactive tile overwrites whatever shares its cell - so this second view is what a cell falls
        // back to when its interactive tile is retired (brick broken, coin collected). Without it the
        // walkable decoration under a brick would vanish from collision the moment the brick did, while
        // still being drawn, and the player would fall through it.
        final Tile[][] underlayTiles = levelScene.getTilesOfLayersBelow(INTERACTIVE_OBJECTS);

        // Collect all non-NULL_TILE ids so we can bulk-fetch their records. Both views contribute: a tile
        // covered by an interactive one appears only in the underlay.
        final Set<Integer> nonNullIds = new HashSet<>();
        for (final Tile[][] view : new Tile[][][] {tiles, underlayTiles}) {
            for (final Tile[] row : view) {
                for (final Tile tile : row) {
                    if (tile != NULL_TILE) {
                        nonNullIds.add(tile.getId());
                    }
                }
            }
        }

        final LevelObjectService levelObjectService = getBean(LevelObjectService.class);
        final Map<Integer, LevelObjectRecord> recordsById = levelObjectService.findAllByIds(nonNullIds);

        final LevelObject[][] objects = toLevelObjects(gameEngine, tiles, dimensions, recordsById);
        final LevelObject[][] underlayObjects = toLevelObjects(gameEngine, underlayTiles, dimensions, recordsById);

        // Each Animatable object registers itself with its own animator singleton.
        // No type checks here - adding new animated object types costs zero lines.
        // Only the surface view registers: an underlay object is a stand-in that may never be exposed, and
        // registering it would have its animator paint over the tile that currently covers it.
        final Animator.Registry animatorRegistry = getBean(Animator.Registry.class);
        animatorRegistry.resetAll(GameObjectAnimator.class);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (objects[row][col] instanceof final AnimatableLevelObject animatableLevelObject) {
                    final GameObjectAnimator<AnimatableLevelObject> animator = animatorRegistry
                        .findSuitableAnimator(animatableLevelObject.getType());
                    animator.add(animatableLevelObject);
                }
            }
        }

        // The P-Switch window belongs to the grid and lives exactly as long as it does; a rebuilt grid
        // (new level, or a re-run in the editor's tester) starts closed.
        final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
        powerSwitchTimeWindow.reset();

        final StaticEnvironmentCollisionGrid collisionGrid = getBean(
            StaticEnvironmentCollisionGrid.class,
            gameEngine,
            powerSwitchTimeWindow
        );
        collisionGrid.setObjects(objects);
        collisionGrid.setUnderlayObjects(underlayObjects);
        collisionGrid.setDimensions(dimensions);
        return collisionGrid;
    }

    /**
     * Turns one consolidated tile view into its collision objects. A cell becomes an interactive object
     * only when its record names a <b>single-tiled</b> type — that is what a tile-bound object is. Cells
     * with no tile, no record, or a multi-tiled record fall back to the tile's own category: solid for
     * {@code COLLIDING}/{@code ONE_WAY_PLATFORM}, {@code EMPTY_LEVEL_OBJECT} otherwise.
     */
    private LevelObject[][] toLevelObjects(
        final GameEngine gameEngine,
        final Tile[][] tiles,
        final LevelSceneDimensions dimensions,
        final Map<Integer, LevelObjectRecord> recordsById
    ) {
        final LevelObject[][] objects = new LevelObject[dimensions.rows()][dimensions.columns()];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }

        for (int row = 0; row < dimensions.rows(); row++) {
            for (int col = 0; col < dimensions.columns(); col++) {
                final Tile tile = tiles[row][col];
                final TileType tileType = tile.getType();
                if (tileType == null || tileType == NULL) {
                    continue; // already filled with EMPTY_LEVEL_OBJECT
                }

                final LevelObjectRecord record = recordsById.get(tile.getId());
                final Optional<LevelObjectType> levelObjectType = record == null
                    ? Optional.empty()
                    : record.findLevelObjectType();
                if (levelObjectType.isEmpty() || levelObjectType.get().isMultiTiled()) {
                    final TileType.Category category = tile.getType().getCategory();
                    if (category == COLLIDING || category == ONE_WAY_PLATFORM) {
                        final SolidLevelObject newSolidLevelObject = SolidLevelObject
                            .builder()
                            .tile(tile)
                            .type(DUMMY_SOLID_OBJECT)
                            .build();
                        objects[row][col] = newSolidLevelObject;
                    }
                    // non-COLLIDING tiles without a single-tiled object stay as EMPTY_LEVEL_OBJECT
                    continue;
                }

                objects[row][col] = record.toLevelObject(
                    gameEngine,
                    Offset.of(col, row)
                );
                objects[row][col].configure(new LevelObjectData(record.getData()));
            }
        }
        return objects;
    }
}
