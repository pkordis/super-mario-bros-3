package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.SoundEffect.TAIL_WAG;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_UP;

public interface LevelScenePlayerActionCapable
    extends
        LevelScenePlayerKickCapable,
        LevelScenePlayerMoveCapable {
    default void handleDucking(final PlayerInputHandler inputHandler) {
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);
        final PlayerRuntimeState runtimeState = getRuntimeState();

        // On a slope, Down slides instead of ducking
        if (isSmall() || runtimeState.isCarrying() || runtimeState.isSliding() || runtimeState.isOnSlope()) {
            runtimeState.standUp();
        } else if (!runtimeState.isInAir()) {
            // Grounded: re-evaluate ducking from pad input each frame.
            // Duck when ONLY down is held.
            if (inputDown) {
                runtimeState.duck();
            } else {
                runtimeState.standUp();
            }
        }
        // In air: ducking flag is frozen (if already
        // ducking when airborne, keep it; if not, keep it unset). No action
        // needed — the flag persists from whatever it was before takeoff.
    }

    default void tickTailAttackCountdown() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (runtimeState.getPlayerTailAttackCountdown() > 0) {
            runtimeState.setPlayerTailAttackCountdown(runtimeState.getPlayerTailAttackCountdown() - 1);
        }
    }

    default void handleTailAttack(final PlayerInputHandler inputHandler) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);

        // Cannot initiate while ducking or sliding
        if (!inputDown && !runtimeState.isSliding() && runtimeState.getPlayerTailAttackCountdown() == 0) {
            if (inputHandler.consumePress(HANDLER_RUN)) {
                runtimeState.setPlayerTailAttackCountdown(18);
                getSoundPlayer().play(TAIL_WAG);
            }
        }
    }

    /**
     * Starts, runs and ends a slide down a slope, once the collision pass has settled the ground under the player.
     * See {@code kb/slopes.md}, "Sliding".
     *
     * @param inputHandler the pad
     * @param hitWall      whether the collision pass found a wall in front of the player
     */
    default void handleSliding(
        final PlayerInputHandler inputHandler,
        final boolean hitWall
    ) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);
        final boolean inputLeft = inputHandler.isActive(HANDLER_LEFT);
        final boolean inputRight = inputHandler.isActive(HANDLER_RIGHT);

        if (runtimeState.isSliding()) {
            if (runtimeState.isInAir() || inputLeft || inputRight || inputHandler.isActive(HANDLER_UP)) {
                runtimeState.stopSliding();
                return;
            }
            if (runtimeState.isSlidingAgainstAWall()) {
                // Still pushing downhill into the wall, without moving, for as long as Down is held
                position.setDX(0);
                if (!inputDown || !runtimeState.isOnSlope()) {
                    runtimeState.stopSliding();
                }
                return;
            }
            if (hitWall && position.getDX() == 0) {
                if (inputDown) {
                    runtimeState.slideAgainstAWall();
                } else {
                    runtimeState.stopSliding();
                }
                return;
            }
        } else if (runtimeState.isInAir() || !runtimeState.isOnSlope() || runtimeState.isCarrying() || !inputDown
            || inputLeft || inputRight) {
            return;
        }
        runtimeState.advanceSlide();
        position.setDX(runtimeState.getSlideVelocity() / (double) TILE_SPRITE_SIZE);
    }
}
