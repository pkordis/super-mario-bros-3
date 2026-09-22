package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.EnemySpawner;
import house.x1337.app.smb3.util.CastCapable;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface ActiveObjectGridCapabilities extends CastCapable {
    default ActiveObjectGrid<ActiveLevelObject> toActiveObjectGrid(final GameEngine gameEngine) {
        return checkedCast(
            getBean(
                ActiveObjectGrid.class,
                getBean(
                    EnemySpawner.class,
                    gameEngine
                )
            )
        );
    }
}
