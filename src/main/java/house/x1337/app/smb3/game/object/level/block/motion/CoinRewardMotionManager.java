package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.block.animation.CoinPopAnimation;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.parabolic;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.poppedOutCoin;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.spinning;
import static house.x1337.app.smb3.game.object.level.KillTallyAware.KILL_TALLY_LADDER;
import static house.x1337.app.smb3.game.object.level.KillTallyAware.MAX_KILL_TALLY;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.min;

@Getter
@Singleton
public final class CoinRewardMotionManager implements MotionManager {
    public static final float SCORE_X_OFFSET_FROM_COIN = -4.0f / TILE_SPRITE_SIZE;
    public static final PopMotion BLOCK_COIN_MOTION = parabolic(11, 647, 32, 128, 38).spinning(4, 4, 2);
    public static final PopMotion POPPED_OUT_COIN_MOTION = spinning(poppedOutCoin(), 4, 4, 0);
    static final int POPPED_OUT_COIN_X_OFFSET_PIXELS = 4;
    static final int POPPED_OUT_CAPTION_ABOVE_COIN_PIXELS = 6;
    static final int POPPED_OUT_FIRST_LADDER_INDEX = 4;
    private static final float CAPTION_INITIAL_DIP = 4f / TILE_SPRITE_SIZE;

    private final List<FlyingCoin> activeCoins = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScores = new ArrayList<>();

    @Override
    public void update() {
        final Iterator<FlyingCoin> coinIterator = activeCoins.iterator();
        while (coinIterator.hasNext()) {
            final FlyingCoin coin = coinIterator.next();
            coin.animation().tick();
            if (coin.animation().isExpired()) {
                coin.landing().accept(coin.animation());
                coin.animation().detach();
                coinIterator.remove();
            }
        }
        tickScores();
    }

    @Override
    public void updateWhileHalted() {
        tickScores();
    }

    /**
     * Pops a coin out of a block.
     *
     * @param gameEngine the game engine
     * @param reward     what the caption shows and the future completes with
     * @param offset     the block's cell
     * @return completed with the caption's points when the coin lands
     */
    public CompletableFuture<Integer> spawnCoinReward(
        final GameEngine gameEngine,
        final Reward reward,
        final Coordinates offset
    ) {
        final CoinPopAnimation coin = getBean(
            CoinPopAnimation.class,
            gameEngine,
            reward.getData(),
            offset,
            BLOCK_COIN_MOTION
        );
        final Dimensions dimensions = coin.getDimensions();
        coin.setWorldOffset(WorldOffset.of(
            offset.x() + dimensions.width() - dimensions.width() / 2,
            getLevelSceneRows(gameEngine.getLevelScene()) - 1 - offset.y() + dimensions.height(),
            Z_DEPTH_ITEM_REWARD
        ));
        coin.start();
        final CompletableFuture<Integer> completion = new CompletableFuture<>();
        activeCoins.add(new FlyingCoin(coin, landed -> landBlockCoin(landed, completion)));
        return completion;
    }

    /**
     * Pops a coin out where an object was.
     *
     * @param player         the player who touched the card, who gets the coin and the score
     * @param objectPosition the object's top-left, in level pixels
     */
    public void spawnPoppedOutCoin(
        final LevelScenePlayer player,
        final PixelPosition objectPosition
    ) {
        final GameEngine gameEngine = player.getGameEngine();
        final CoinPopAnimation coin = getBean(
            CoinPopAnimation.class,
            gameEngine,
            SCORE_1000.getData(),
            of(objectPosition.column(), objectPosition.row()),
            POPPED_OUT_COIN_MOTION
        );
        coin.setWorldOffset(
            worldOffsetOf(gameEngine, objectPosition.x() + POPPED_OUT_COIN_X_OFFSET_PIXELS, objectPosition.y())
        );
        coin.start();
        activeCoins.add(new FlyingCoin(coin, landed -> landPoppedOutCoin(landed, player, objectPosition)));
    }

    /**
     * Drops any in-flight coin pops and score captions.
     */
    @Override
    public void reset() {
        activeCoins.forEach(coin -> coin.animation().detach());
        activeCoins.clear();
        activeScores.forEach(ScorePopupAnimation::detach);
        activeScores.clear();
    }

    private void landBlockCoin(
        final CoinPopAnimation coin,
        final CompletableFuture<Integer> completion
    ) {
        final WorldOffset captionOffset = coin.getCurrentWorldOffset().plus(SCORE_X_OFFSET_FROM_COIN, 0, 0);
        final ScorePopupAnimation caption = spawnCaption(coin, coin.getRewardData(), captionOffset);
        completion.complete(caption.getRewardData().getPoints());
    }

    private void landPoppedOutCoin(
        final CoinPopAnimation coin,
        final LevelScenePlayer player,
        final PixelPosition objectPosition
    ) {
        final PlayerData playerData = player.getPlayerData();
        playerData.addCoin();
        final int tally = player.getRuntimeState().nextKillTally();
        final Reward reward = KILL_TALLY_LADDER[min(POPPED_OUT_FIRST_LADDER_INDEX + tally, MAX_KILL_TALLY)];
        playerData.add(reward.getData());

        final int risen = coin.getMotion().verticalOffsetAt(coin.getMotion().durationTicks());
        final double captionTop = objectPosition.y() - risen - POPPED_OUT_CAPTION_ABOVE_COIN_PIXELS;
        final WorldOffset captionOffset = worldOffsetOf(coin.getGameEngine(), objectPosition.x(), captionTop);
        spawnCaption(coin, reward.getData(), captionOffset.plus(0f, CAPTION_INITIAL_DIP, 0f));
    }

    private ScorePopupAnimation spawnCaption(
        final CoinPopAnimation coin,
        final Reward.Data rewardData,
        final WorldOffset worldOffset
    ) {
        final ScorePopupAnimation caption = getBean(
            ScorePopupAnimation.class,
            coin.getGameEngine(),
            rewardData,
            coin.getOffset()
        );
        caption.setWorldOffset(worldOffset);
        caption.start();
        activeScores.add(caption);
        return caption;
    }

    private void tickScores() {
        final Iterator<ScorePopupAnimation> iterator = activeScores.iterator();
        while (iterator.hasNext()) {
            final ScorePopupAnimation caption = iterator.next();
            caption.tick();
            if (caption.isExpired()) {
                caption.detach();
                iterator.remove();
            }
        }
    }

    private int getLevelSceneRows(final LevelScene levelScene) {
        return levelScene.getDimensions().rows();
    }

    private WorldOffset worldOffsetOf(
        final GameEngine gameEngine,
        final double pixelX,
        final double pixelTop
    ) {
        return WorldOffset.of(
            (float) (pixelX / TILE_SPRITE_SIZE),
            getLevelSceneRows(gameEngine.getLevelScene()) - (float) (pixelTop / TILE_SPRITE_SIZE),
            Z_DEPTH_ITEM_REWARD
        );
    }

    record FlyingCoin(CoinPopAnimation animation, Consumer<CoinPopAnimation> landing) {
    }
}
