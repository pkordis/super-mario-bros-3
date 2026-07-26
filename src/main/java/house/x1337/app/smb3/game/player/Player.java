package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

public interface Player
    extends
        ActivePlayerStateAware,
        PlayerModeAware {
    void renderPlayer();
    void updateFrame();
    void updateVisualPosition();

    /**
     * Interpolates the player's visual (node) position between the previous
     * and current simulation positions. Called once per render frame after the
     * simulation loop to eliminate jitter when the render rate exceeds the
     * simulation rate.
     *
     * @param alpha blend factor in [0, 1] — 0 = previous tick position,
     *              1 = current tick position
     */
    void interpolateVisualPosition(double alpha);

    void updateInCameraState(CameraState cameraState);

    PlayerPosition getPosition();
}
