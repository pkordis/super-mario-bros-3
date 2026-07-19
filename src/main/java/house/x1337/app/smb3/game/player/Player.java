package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

public interface Player {
    void renderUpdate();
    void setMode(PlayerMode playerMode);
    void updateFrame();
    void updateVisualPosition();
    void updateInCameraState(CameraState cameraState);

    PlayerMode getMode();
    PlayerPosition getPosition();
    ActivePlayerState getState();

    default boolean isSmall() {
        return getMode().isSmall();
    }

    default boolean isLarge() {
        return getMode().isLarge();
    }
}
