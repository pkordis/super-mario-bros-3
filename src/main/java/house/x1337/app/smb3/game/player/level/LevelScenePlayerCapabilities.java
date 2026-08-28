package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VEL_THRESHOLD;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.abs;

public sealed interface LevelScenePlayerCapabilities
    extends
        LevelScenePlayerRenderer,
        LevelScenePlayerActionEventListener
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

    default boolean isCurrentlySkidding(
        final boolean inputLeft,
        final boolean inputRight
    ) {
        if (getState().isInAir()) {
            return false;
        }
        final double dx = getPosition().getDX();
        if (abs(dx) < PLAYER_SKID_VEL_THRESHOLD) {
            return false;
        }
        // Pressing opposite direction from current movement
        return (dx > 0 && inputLeft) || (dx < 0 && inputRight);
    }

    ActivePlayerState getState();
    PlayerPosition getPosition();
    PlayerData getPlayerData();
}
