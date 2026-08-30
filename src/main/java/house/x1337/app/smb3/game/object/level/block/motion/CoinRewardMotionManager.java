package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.block.animation.CoinPopAnimation;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.Pending;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * Manages coin pop and score popup animations spawned when hitting ? blocks.
 *
 * <p>When a ? block containing a single coin is hit from below, two coordinated
 * animations play in sequence:
 * <ol>
 *   <li>A coin pops up and arcs back down (38 ticks), ending 15 px above its spawn point</li>
 *   <li>When the coin expires, a "100" score popup spawns at its final position
 *       and rises for 48 ticks</li>
 * </ol>
 *
 * <h2>Reference: dasm prg007.asm</h2>
 * <ul>
 *   <li>Coin: {@code CoinPUps_DrawAndUpdate} (lines 2764-2850)</li>
 *   <li>Score spawn: {@code PRG007_AE28} — spawns score when coin YVel == 5</li>
 *   <li>Score: {@code Scores_GiveAndDraw} (lines 2110-2400)</li>
 * </ul>
 */
@Singleton
@RequiredArgsConstructor
public final class CoinRewardMotionManager implements MotionManager {
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
            coinPopAnimation.getScoreData(),
            coinPopAnimation.getOffset(),
            coinPopAnimation.getCurrentWorldOffset().plus(SCORE_X_OFFSET_FROM_COIN, 0, 0)
        );
        completion.complete(scorePopupAnimation.getScoreData().getValue());
        activeScores.add(scorePopupAnimation);

        // TODO: Award 100 points to player score
        // TODO: Increment player coin counter
    }

    public CompletableFuture<Integer> spawnCoinReward(
        final GameEngine gameEngine,
        final Score score,
        final Offset offset
    ) {
        // Spawn coin animation
        final CoinPopAnimation coinPopAnimation = getBean(
            CoinPopAnimation.class,
            gameEngine,
            score.getData(),
            offset
        );
        final CompletableFuture<Integer> completion = new CompletableFuture<>();
        activeCoins.add(new Pending<>(coinPopAnimation, completion));

        // TODO: Play coin sound (SND_LEVELCOIN)

        return completion;
    }
}
