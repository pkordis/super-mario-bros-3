package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

public interface Player
    extends
        PlayerRuntimeStateAware,
        PlayerModeAware {
    void renderPlayer();
    void updateFrame();
    void updateVisualPosition();
    void interpolateVisualPosition(double alpha);
    void updateInCameraState(CameraState cameraState);
    PlayerPosition getPosition();

    /**
     * Whether this player is currently halting gameplay (dasm
     * {@code Player_HaltGame}, prg008 PRG008_A1B4 — set while dying, moving
     * through a pipe, losing a suit, star wearing off, or growing/shrinking).
     * While any player halts gameplay the countdown timer, all active objects,
     * the camera and every other player freeze; only the halting player's own
     * transition advances. Defaults to {@code false}; the level player overrides
     * it for the grow transition.
     */
    default boolean isHaltingGameplay() {
        return false;
    }
}
