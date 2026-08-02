package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.SolidLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.DUMMY_SOLID_OBJECT;
import static house.x1337.app.smb3.enumeration.TileType.Category.COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.ONE_WAY_PLATFORM;
import static house.x1337.app.smb3.enumeration.TileType.NULL;

public interface CollisionGridCapabilities {
    default CollisionGrid toCollisionGrid(final LevelScenePlayer levelScenePlayer) {
        final LevelScene levelScene = (LevelScene) this;
        final int rows = levelScene.getDimensions().rows();
        final int columns = levelScene.getDimensions().columns();
        final LevelSceneDimensions dimensions = new LevelSceneDimensions(columns, rows);
        final Tile[][] tiles = levelScene.getTilesOfConsolidatedLayers();

        // Collect all non-NULL_TILE ids so we can bulk-fetch their records.
        final Set<Integer> nonNullIds = new HashSet<>();
        for (final Tile[] row : tiles) {
            for (final Tile tile : row) {
                if (tile != NULL_TILE) {
                    nonNullIds.add(tile.getId());
                }
            }
        }

        final LevelObjectService levelObjectService = getBean(LevelObjectService.class);
        final Map<Integer, LevelObjectRecord> recordsById = levelObjectService.findAllByIds(nonNullIds);

        final LevelObject[][] objects = new LevelObject[rows][columns];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                final Tile tile = tiles[row][col];
                final TileType tileType = tile.getType();
                if (tileType == null || tileType == NULL) {
                    continue; // already filled with EMPTY_LEVEL_OBJECT
                }

                final LevelObjectRecord record = recordsById.get(tile.getId());
                if (record == null || record.getType() == null) {
                    final TileType.Category category = tile.getType().getCategory();
                    if (category == COLLIDING || category == ONE_WAY_PLATFORM) {
                        final SolidLevelObject newSolidLevelObject = SolidLevelObject
                            .builder()
                            .tile(tile)
                            .type(DUMMY_SOLID_OBJECT)
                            .build();
                        objects[row][col] = newSolidLevelObject;
                    }
                    // non-COLLIDING tiles with no record stay as EMPTY_LEVEL_OBJECT
                    continue;
                }

                objects[row][col] = record.toLevelObject(Offset.of(col, row));
                objects[row][col].configure(record.getData());
            }
        }

        // Each Animatable object registers itself with its own animator singleton.
        // No type checks here - adding new animated object types costs zero lines.
        final GameObjectAnimator.Registry gameObjectAnimatorRegistry = getBean(GameObjectAnimator.Registry.class);
        gameObjectAnimatorRegistry.resetAll();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (objects[row][col] instanceof final AnimatableLevelObject animatableLevelObject) {
                    final GameObjectAnimator<AnimatableLevelObject> animator = gameObjectAnimatorRegistry
                        .findSuitableAnimator(animatableLevelObject.getClass());
                    animator.add(animatableLevelObject);
                }
            }
        }

        return new CollisionGrid(
            levelScenePlayer,
            objects,
            dimensions,
            levelScenePlayer.getGameEngine()
        );
    }
}
