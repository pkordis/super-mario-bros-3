package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.block.animation.CoinPopAnimation;
import house.x1337.app.smb3.game.object.level.reward.Coin;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.Pending;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
@RequiredArgsConstructor
public final class CoinRewardMotionManager implements MotionManager<Coin> {
    public static final float SCORE_X_OFFSET_FROM_COIN = -4.0f / TILE_SPRITE_SIZE;

    private final List<Pending<CoinPopAnimation, Integer>> activeCoins = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScores = new ArrayList<>();

    @Override
    public void update() {
        // Update coin animations and check for expired coins
        final Iterator<Pending<CoinPopAnimation, Integer>> coinPopAnimationIterator = activeCoins.iterator();
        while (coinPopAnimationIterator.hasNext()) {
            final Pending<CoinPopAnimation, Integer> pendingCoin = coinPopAnimationIterator.next();
            final CoinPopAnimation coinPopAnimation = pendingCoin.completable();
            coinPopAnimation.tick();
            if (coinPopAnimation.isExpired()) {
                // Spawn score popup at coin's final position, carrying over the completion handle
                spawnScorePopupForExpiredCoin(coinPopAnimation, pendingCoin.completion());
                coinPopAnimation.detach();
                coinPopAnimationIterator.remove();
            }
        }

        // Update score popup animations
        final Iterator<ScorePopupAnimation> scorePopupAnimationIterator = activeScores.iterator();
        while (scorePopupAnimationIterator.hasNext()) {
            final ScorePopupAnimation scorePopupAnimation = scorePopupAnimationIterator.next();
            scorePopupAnimation.tick();
            if (scorePopupAnimation.isExpired()) {
                scorePopupAnimation.detach();
                scorePopupAnimationIterator.remove();
            }
        }
    }

    private void spawnScorePopupForExpiredCoin(
        final CoinPopAnimation coinPopAnimation,
        final CompletableFuture<Integer> completion
    ) {
        final ScorePopupAnimation scorePopupAnimation = getBean(
            ScorePopupAnimation.class,
            coinPopAnimation.getGameEngine(),
            coinPopAnimation.getRewardData(),
            coinPopAnimation.getOffset()
        );
        final WorldOffset worldOffset = coinPopAnimation.getCurrentWorldOffset().plus(SCORE_X_OFFSET_FROM_COIN, 0, 0);
        scorePopupAnimation.setWorldOffset(worldOffset);
        scorePopupAnimation.start();
        completion.complete(scorePopupAnimation.getRewardData().getPoints());
        activeScores.add(scorePopupAnimation);
    }

    public CompletableFuture<Integer> spawnCoinReward(
        final GameEngine gameEngine,
        final Reward reward,
        final Offset offset
    ) {
        // Spawn coin animation
        final CoinPopAnimation coinPopAnimation = getBean(
            CoinPopAnimation.class,
            gameEngine,
            reward.getData(),
            offset
        );
        coinPopAnimation.setWorldOffset(null);
        coinPopAnimation.start();
        final CompletableFuture<Integer> completion = new CompletableFuture<>();
        activeCoins.add(new Pending<>(coinPopAnimation, completion));

        return completion;
    }
}
