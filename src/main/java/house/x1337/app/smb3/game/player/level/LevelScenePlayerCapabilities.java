package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.enumeration.*;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.SolidLevelObject;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.DUMMY_SOLID_OBJECT;
import static house.x1337.app.smb3.enumeration.PlayerOrientation.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.TileType.Category.COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.Category.ONE_WAY_PLATFORM;
import static house.x1337.app.smb3.enumeration.TileType.NULL;
import static house.x1337.app.smb3.game.player.factory.PlayerAnimatorFactory.contextForLevel;

// TODO: relocate constants
@Slf4j
public sealed abstract class LevelScenePlayerCapabilities
    implements
        LevelScenePlayerRenderer,
        LevelScenePlayerActionEventListener,
        Player
    permits
        LevelScenePlayer {
    @Getter
    final GameEngine gameEngine;
    final PlayerInputHandler inputHandler;

    /** The node containing the player geometry (attached to rootNode). */
    @Getter
    private Node node;
    final CollisionGrid collisionGrid;
    @Getter
    final PlayerPosition position;
    @Getter
    private PlayerMode mode;
    @Getter
    @Setter
    private PlayerVisibility visibility = FOREGROUND;
    @Getter
    final ActivePlayerState state = getBean(ActivePlayerState.class);
    @Getter
    @Setter
    private PlayerOrientation playerOrientation = RIGHT;
    @Getter
    final PlayerData playerData;

    /** Animator for raccoon mode sprite rendering and walk animation. */
    @Getter
    private final LevelScenePlayerAnimationContext playerAnimationContext;

    public LevelScenePlayerCapabilities(
        final GameEngine gameEngine,
        final PlayerData playerData
    ) {
        this.gameEngine = gameEngine;
        this.playerData = playerData;
        this.inputHandler = getBean(
            PlayerInputHandler.class,
            gameEngine
        );
        this.position = initializePosition();
        this.collisionGrid = createCollisionGrid();
        this.playerAnimationContext = contextForLevel(this);
    }

    @Override
    public PlayerIdentity getIdentity() {
        return playerData.getIdentity();
    }

    @Override
    public void renderPlayer() {
        node = createNode();
        gameEngine
            .getRootNode()
            .attachChild(node);
        playerAnimationContext.loadAssets();
        updateVisualPosition();
    }

    @Override
    public void setMode(final PlayerMode playerMode) {
        this.mode = playerMode;
        if (node != null) {
            rebuildGeometry(node);
            updateVisualPosition();
        }
    }

    private PlayerPosition initializePosition() {
        final LevelScene levelScene = getLevelScene();
        final PlayerPosition position = new PlayerPosition();
        // Convert tile coords to sprite-pixel coords
        position.setX(levelScene.getSpawnPointColumn() * TILE_SPRITE_SIZE);
        position.setY(levelScene.getSpawnPointRow() * TILE_SPRITE_SIZE);
        position.setDX(0);
        position.setDY(0);
        return position;
    }

    CollisionGrid createCollisionGrid() {
        final LevelScene levelScene = getLevelScene();
        if (levelScene == null) {
            return null;
        }

        final int rows = levelScene.getRows();
        final int columns = levelScene.getColumns();
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
                        log.debug("Added: {} at {}x{}", newSolidLevelObject,  row, col);
                    }
                    // non-COLLIDING tiles with no record stay as EMPTY_LEVEL_OBJECT
                    continue;
                }

                objects[row][col] = record.toLevelObject();
            }
        }

        return new CollisionGrid(this, objects, rows, columns);
    }


    @Override
    public void updateInCameraState(final CameraState cameraState) {
        // Point the camera at the player node so it follows the player
        cameraState.setTarget(node);
    }

    @Override
    public void advanceAnimation() {
        playerAnimationContext.update((LevelScenePlayer) this);
    }
}
