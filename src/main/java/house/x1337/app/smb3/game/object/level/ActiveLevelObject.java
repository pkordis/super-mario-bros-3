package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

public interface ActiveLevelObject extends GameEngineAware, LevelObject {
    boolean intersects(LevelScenePlayer player);
    Geometry getSpriteGeometry();

    default LevelScenePlayer findCollidingPlayer() {
        for (final Player candidate : getGameEngine().getPlayers()) {
            if (candidate instanceof final LevelScenePlayer levelScenePlayer && intersects(levelScenePlayer)) {
                return levelScenePlayer;
            }
        }
        return null;
    }

    default void detach() {
        getGameEngine()
            .getRootNode()
            .detachChild(getSpriteGeometry());
    }
}
