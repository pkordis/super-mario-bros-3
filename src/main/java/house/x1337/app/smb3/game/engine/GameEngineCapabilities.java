package house.x1337.app.smb3.game.engine;

import com.jme3.app.SimpleApplication;
import house.x1337.app.smb3.event.GameEventBusAware;

public sealed abstract class GameEngineCapabilities
    extends
        SimpleApplication
    implements
        GameEventBusAware,
        GameEngineRenderer
    permits
        GameEngine {
    @Override
    public void destroy() {
        super.destroy();
        getGameEventBus().purgeEventListeners();
    };
}

