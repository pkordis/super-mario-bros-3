package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientation;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.game.engine.PlayerData;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import lombok.Getter;
import lombok.Setter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PlayerOrientation.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.game.player.factory.PlayerAnimatorFactory.contextForLevel;

// TODO: relocate constants
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
    private LevelScenePlayerAnimationContext playerAnimationContext;

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

        return new CollisionGrid(
            this,
            levelScene.getTilesOfConsolidatedLayers(),
            levelScene.getRows(),
            levelScene.getColumns()
        );
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
