package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_END_LEVEL_X_VELOCITY;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;

public interface LevelScenePlayerRunningOffCapabilities
    extends
        LevelScenePlayerOrientationAware,
        LevelScenePlayerRenderer,
        LevelScenePlayerDataAware {
    int RUN_OFF_LEFT_FOOT_OFFSET = 4;

    PlayerInputHandler getInputHandler();
    void updateVerticalScroll();

    default void startRunningOff() {
        getRuntimeState().setRunningOffLevel(true);
        getInputHandler().ignorePlayerInput();
        applyRunOffWalk();
    }

    default boolean isRunningOff() {
        return getRuntimeState().isRunningOffLevel();
    }

    default void haltRunOff() {
        getRuntimeState().setRunOffHalted(true);
    }

    default boolean isRunOffHalted() {
        return getRuntimeState().isRunOffHalted();
    }

    default void applyRunOffWalk() {
        getOrientation().setHorizontal(RIGHT);
        getPosition().setDX(getRuntimeState().isInAir() ? 0 : PLAYER_END_LEVEL_X_VELOCITY);
    }

    default boolean isPastTheLevelsRightEdge() {
        final PlayerPosition position = getPosition();
        final int levelWidthPixels = getLevelScene().getDimensions().columns() * TILE_SPRITE_SIZE;
        return position.getX() + position.getDX() + RUN_OFF_LEFT_FOOT_OFFSET >= levelWidthPixels;
    }

    default void tickBeyondTheLevel() {
        final PlayerPosition position = getPosition();
        final PlayerRuntimeState runtimeState = getRuntimeState();
        position.snapshotPrevious();
        runtimeState.setTo(WALKING);
        position.setDY(0);
        position.setDX(PLAYER_END_LEVEL_X_VELOCITY);
        position.addToX(PLAYER_END_LEVEL_X_VELOCITY);
        handlePowerMeterAndRunFlag(getInputHandler());
        advanceAnimation();
        updateVerticalScroll();
        updateVisualPosition();
    }
}
