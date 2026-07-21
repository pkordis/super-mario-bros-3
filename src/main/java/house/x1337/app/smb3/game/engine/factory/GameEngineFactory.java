package house.x1337.app.smb3.game.engine.factory;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.PlayerData;
import house.x1337.app.smb3.jme3.core.CameraState;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface GameEngineFactory {
    static GameEngine start() {
        return getBean(
            GameEngine.class,
            getBean(CameraState.class),
            getBean(PlayerData.class)
        );
    }
}
