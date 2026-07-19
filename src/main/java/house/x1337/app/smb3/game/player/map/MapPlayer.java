package house.x1337.app.smb3.game.player.map;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import lombok.Getter;

@Prototype
public class MapPlayer implements Player {
    @Getter
    private final ActivePlayerState state = new ActivePlayerState();

    @Override
    public void renderPlayer() {
    }

    @Override
    public void setMode(final PlayerMode playerMode) {
    }

    @Override
    public void updateFrame() {
    }

    @Override
    public void updateVisualPosition() {
    }

    @Override
    public void updateInCameraState(final CameraState cameraState) {
    }

    @Override
    public PlayerMode getMode() {
        return null;
    }

    @Override
    public PlayerPosition getPosition() {
        return null;
    }
}
