package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.player.RewardConsumingPlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public sealed interface LevelScenePlayerCapabilities
    extends
        LevelScenePlayerRenderer,
        LevelScenePlayerActionCapable,
        LevelScenePlayerActionEventListener,
        RewardConsumingPlayer
    permits
        LevelScenePlayer {
    void onTransitionComplete(PlayerMode newPlayerMode);
    LevelSceneVerticalScroll getVerticalScroll();

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

    /**
     * Advances whichever transition is running by one tick, and completes it into the mode that
     * transition queued (dasm {@code Player_QueueSuit}).
     *
     * <p>The destination is read from the runtime state rather than implied by the counter, because the
     * poof counter is shared by two transitions travelling in opposite directions: the Super Leaf's
     * promotion to {@code RACCOON} and the suit loss back down to {@code NORMAL}.
     *
     * <p>The post-hit flash ({@code Player_FlashInv}) advances here <em>only on the grow/shrink path</em>,
     * mirroring the ROM's draw dispatcher precisely. A poof plays {@code Player_SuitLost_DoPoof} and
     * {@code RTS}es before ever reaching {@code Player_Draw} (prg029 @ PRG029_D205), so its counter is
     * frozen for the whole cloud — which is why the flash can be armed at hurt time and still start only
     * once the poof is over. A grow/shrink is different: it falls
     * through to {@code JSR Player_Draw} (prg029 @ PRG029_D224 → PRG029_D238), and {@code Player_Draw}
     * is exactly where {@code DEC Player_FlashInv} lives (@ PRG029_CECB). So a player shrinking from a
     * hit flickers all the way through the shrink, not only after it. The decrement runs after
     * {@code advanceAnimation} so this tick's visibility came from the pre-decrement value, matching the
     * ROM's {@code LDA}/{@code DEC} order (@ PRG029_CEC8).
     */
    default void tickModeTransition() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        getPosition().snapshotPrevious();
        advanceAnimation();
        updateVisualPosition();
        if (runtimeState.isTurningToRaccoon()) {
            runtimeState.decrementPoof();
            if (!runtimeState.isTurningToRaccoon()) {
                onTransitionComplete(runtimeState.getQueuedMode());
            }
            return;
        }
        runtimeState.decrementHurtInvincibility();
        runtimeState.decrementGrow();
        if (!runtimeState.isChangingSize()) {
            onTransitionComplete(runtimeState.getQueuedMode());
        }
    }
}
