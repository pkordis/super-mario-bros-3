package house.x1337.app.smb3.game.engine;

import com.jme3.asset.AssetManager;
import house.x1337.app.smb3.enumeration.GameContext;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;

public interface GameEngineAware {
    GameEngine getGameEngine();

    default AssetManager getAssetManager() {
        return getGameEngine().getAssetManager();
    }

    default LevelScene getLevelScene() {
        return getGameEngine().getLevelScene();
    }

    default GameContext getGameContext() {
        return getGameEngine().getGameContext();
    }

    default StaticEnvironmentCollisionGrid getCollisionGrid() {
        return getGameEngine().getCollisionGrid();
    }
}
