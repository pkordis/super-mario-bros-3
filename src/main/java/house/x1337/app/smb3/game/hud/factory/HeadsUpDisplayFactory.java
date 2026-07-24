package house.x1337.app.smb3.game.hud.factory;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.hud.HeadsUpDisplay;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface HeadsUpDisplayFactory {
    static HeadsUpDisplay create(final GameEngine gameEngine) {
        return getBean(
            HeadsUpDisplay.class,
            gameEngine,
            gameEngine.getPlayerData()
        );
    }
}
