package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCardSpawner;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.event.LevelCompleted;
import lombok.Getter;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Getter
@Prototype
public final class LevelCompletion implements GameEngineAware {
    private final GameEngine gameEngine;
    private final LevelCompletionCardSpawner cardSpawner;

    private LevelCompletionCardType collectedCard;
    private LevelScenePlayer runningOffPlayer;
    private boolean underway;
    private boolean completed;

    public LevelCompletion(final GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.cardSpawner = getBean(LevelCompletionCardSpawner.class, gameEngine);
    }

    public void reset() {
        underway = false;
        completed = false;
        collectedCard = null;
        runningOffPlayer = null;
        cardSpawner.reset();
    }

    public void spawnCardComingIntoView() {
        cardSpawner.spawnComingIntoView();
    }

    public void collect(
        final LevelCompletionCardType card,
        final LevelScenePlayer player
    ) {
        if (underway) {
            return;
        }
        underway = true;
        collectedCard = card;
        runningOffPlayer = player;
        cardSpawner.retire();

        final PlayerData playerData = player.getPlayerData();
        playerData.addCard(card);
        playerData.getPlayerTimer().pause();
        getMotionManagers(GameObjectAnimator.class).forEach(GameObjectAnimator::freeze);
        getCollisionGrid().getPowerSwitchTimeWindow().reset();
        player.getRuntimeState().setKillTally(0);
        player.getPosition().setDY(0);
        player.startRunningOff();

        gameEngine.publish(new LevelCardCollected(card));
    }

    public void checkPlayerHasRunOffLevel() {
        if (runningOffPlayer == null || completed || runningOffPlayer.getRuntimeState().isDying()) {
            return;
        }
        final double screenRight = gameEngine.getCameraState().getActiveObjectRegion(0).right();
        if (runningOffPlayer.getPosition().getX() > screenRight) {
            completed = true;
            gameEngine.publish(new LevelCompleted());
        }
    }
}
