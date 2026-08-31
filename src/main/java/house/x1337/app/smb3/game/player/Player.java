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
}
