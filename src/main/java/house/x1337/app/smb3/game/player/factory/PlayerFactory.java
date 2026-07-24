package house.x1337.app.smb3.game.player.factory;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.player.map.MapPlayer;

import java.util.function.Consumer;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface PlayerFactory {
    static Player spawn(
        final PlayerData playerData,
        final Consumer<Player> onSpawn,
        final GameEngine gameEngine
    ) {
        final Player player = switch (gameEngine.getGameContext()) {
            case LEVEL_SCENE -> getBean(
                LevelScenePlayer.class,
                gameEngine,
                playerData
            );
            case MAP -> getBean(MapPlayer.class);
        };
        onSpawn.accept(player);
        return player;
    }
}
