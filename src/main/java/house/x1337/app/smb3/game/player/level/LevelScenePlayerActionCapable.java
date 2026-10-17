package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;

public interface LevelScenePlayerActionCapable extends LevelScenePlayerMoveCapable {
    default void handleDucking(final PlayerInputHandler inputHandler) {
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);
        final PlayerRuntimeState runtimeState = getRuntimeState();

        if (isSmall()) {
            // Small Mario cannot duck (dasm prg008 PRG008_A70E: forcefully
            // disable ducking when small/holding/sliding).
            runtimeState.standUp();
        } else if (!runtimeState.isInAir()) {
            // Grounded: re-evaluate ducking from pad input each frame.
            // dasm prg008 PRG008_A72B: duck when ONLY down is held.
            if (inputDown) {
                runtimeState.duck();
            } else {
                runtimeState.standUp();
            }
        }
        // In air: ducking flag is frozen (dasm prg008 PRG008_A715: if already
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

        // Cannot initiate while ducking
        if (!inputDown && runtimeState.getPlayerTailAttackCountdown() == 0) {
            if (inputHandler.consumePress(HANDLER_RUN)) {
                runtimeState.setPlayerTailAttackCountdown(18);
            }
        }
    }
}
