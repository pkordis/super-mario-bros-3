package house.x1337.app.smb3.game.player.map;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import lombok.Getter;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Getter
@Prototype
public class MapPlayer implements Player {
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
    private final SoundPlayer soundPlayer = getBean(SoundPlayer.class);

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
    public void interpolateVisualPosition(final double alpha) {
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
