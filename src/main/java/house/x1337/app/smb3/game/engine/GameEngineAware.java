package house.x1337.app.smb3.game.engine;

import house.x1337.app.smb3.enumeration.GameContext;
import house.x1337.app.smb3.game.LevelScene;

public interface GameEngineAware {
    GameEngine getGameEngine();

    default LevelScene getLevelScene() {
        return getGameEngine().getLevelScene();
    }

    default GameContext getGameContext() {
        return getGameEngine().getGameContext();
    }
}
