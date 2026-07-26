package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public sealed interface LevelScenePlayerCapabilities
    extends
        LevelScenePlayerRenderer,
        LevelScenePlayerActionEventListener,
        Player
    permits
        LevelScenePlayer {
    default PlayerPosition initializePosition() {
        final LevelScene levelScene = getLevelScene();
        final PlayerPosition position = new PlayerPosition();
        // Convert tile coords to sprite-pixel coords
        position.setX(levelScene.getSpawnPointColumn() * TILE_SPRITE_SIZE);
        position.setY(levelScene.getSpawnPointRow() * TILE_SPRITE_SIZE);
        position.setDX(0);
        position.setDY(0);
        // Initialize previous position to match so the first interpolated
        // frame doesn't lerp from the origin.
        position.snapshotPrevious();
        return position;
    }

    default PlayerIdentity getIdentity() {
        return getPlayerData().getIdentity();
    }

    default void renderPlayer() {
        setNode(createNode());
        getGameEngine()
            .getRootNode()
            .attachChild(getNode());
        getPlayerAnimationContext().loadAssets();
        updateVisualPosition();
    }

    Node getNode();
    PlayerData getPlayerData();
    void setNode(Node node);

    /**
     * Frames remaining to suppress wall correction after exiting low
     * clearance. The horizontal probes can detect the ceiling block's
     * edge as a wall on the first frames after the player clears it,
     * causing a snap that jerks the camera.
     */
    int getLowClearanceGrace();
}
