package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.player.RewardConsumingPlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.model.game.Coordinates.of;

public sealed interface LevelScenePlayerCapabilities
    extends
        LevelScenePlayerRenderer,
        LevelScenePlayerActionCapable,
        LevelScenePlayerActionEventListener,
        LevelScenePlayerPipeUsingCapabilities,
        LevelScenePlayerRunningOffCapabilities,
        RewardConsumingPlayer
    permits
        LevelScenePlayer {
    void onTransitionComplete(PlayerMode newPlayerMode);
    LevelSceneVerticalScroll getVerticalScroll();
    PlayerInputHandler getInputHandler();

    /**
     * Places the player on the scene's spawn point. A scene reached only through a pipe may have none: the pipe exit
     * then places the player straight after the build, so the player starts at the scene's top-left until it does.
     *
     * @return the starting position, at rest
     */
    default PlayerPosition initializePosition() {
        final LevelScene levelScene = getLevelScene();
        final Coordinates spawnPoint = levelScene.getSpawnPoint() == null ? of(0, 0) : levelScene.getSpawnPoint();
        final PlayerPosition position = new PlayerPosition();
        // Convert tile coords to sprite-pixel coords
        position.setX(spawnPoint.x() * TILE_SPRITE_SIZE);
        position.setY(spawnPoint.y() * TILE_SPRITE_SIZE);
        position.setDX(0);
        position.setDY(0);
        // Initialize previous position to match so the first interpolated
        // frame doesn't lerp from the origin.
        position.snapshotPrevious();
        return position;
    }

    default void updateVerticalScroll() {
        final PlayerPosition position = getPosition();
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final LevelSceneVerticalScroll verticalScroll = getVerticalScroll();
        final float playerWorldY = (float) position
            .toTileUnitBased(getLevelScene().getDimensions())
            .getY();
        final boolean flying = runtimeState.getPlayerFlyTime() > 0;
        verticalScroll.update(playerWorldY, flying);
    }

    default PlayerIdentity getIdentity() {
        return getPlayerData().getIdentity();
    }

    default void tickModeTransition() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        getPosition().snapshotPrevious();
        advanceAnimation();
        updateVisualPosition();
        if (runtimeState.isTurningToRaccoon()) {
            runtimeState.decrementPoof();
            if (!runtimeState.isTurningToRaccoon()) {
                completeTransition(runtimeState);
            }
            return;
        }
        runtimeState.decrementHurtInvincibility();
        runtimeState.decrementGrow();
        if (!runtimeState.isChangingSize()) {
            completeTransition(runtimeState);
        }
    }

    private void completeTransition(final PlayerRuntimeState runtimeState) {
        onTransitionComplete(runtimeState.getQueuedMode());
        getInputHandler().consumePress(HANDLER_RUN);
    }
}
