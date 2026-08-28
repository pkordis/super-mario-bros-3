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

    /**
     * Handles the raccoon tail attack (dasm prg008: Player_TailAttackAnim).
     *
     * <p>Triggered by a new B press while grounded, not ducking, and not
     * already attacking. Sets {@code playerTailAttack} to 18 ($12) which
     * auto-decrements each frame. The animation plays over 18 frames with
     * the player sprite flipping at frames 11 and 3.
     */
    default void handleTailAttack(final PlayerInputHandler inputHandler) {
        final PlayerRuntimeState runtimeState = getRuntimeState();

        // Decrement counter each frame (at start, so the trigger frame
        // displays at the full $12 value matching the dasm)
        if (runtimeState.getPlayerTailAttackCountdown() > 0) {
            runtimeState.setPlayerTailAttackCountdown(runtimeState.getPlayerTailAttackCountdown() - 1);
        }

        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);

        // Cannot initiate while ducking (dasm: if PAD_DOWN held, skip)
        if (!inputDown && runtimeState.getPlayerTailAttackCountdown() == 0) {
            if (inputHandler.consumePress(HANDLER_RUN)) {
                runtimeState.setPlayerTailAttackCountdown(18);
            }
        }
    }
}
