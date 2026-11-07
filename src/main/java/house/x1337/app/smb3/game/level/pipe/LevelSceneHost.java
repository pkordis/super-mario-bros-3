package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.game.engine.LevelRunStart;
import house.x1337.app.smb3.game.level.scene.LevelScene;

public interface LevelSceneHost {
    void requestLevelSceneRestart();
    void swapLevelScene(LevelScene levelScene, LevelRunStart start);
}
