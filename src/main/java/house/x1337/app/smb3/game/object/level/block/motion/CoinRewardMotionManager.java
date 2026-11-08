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
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.game.motion.pop.BlockCoinPopMotion.SPIN_PHASE_TICKS;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.blockCoin;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.poppedOutCoin;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.spinning;
import static house.x1337.app.smb3.game.object.level.KillTallyAware.KILL_TALLY_LADDER;
import static house.x1337.app.smb3.game.object.level.KillTallyAware.MAX_KILL_TALLY;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.floor;
import static java.lang.Math.min;

@Getter
@Singleton
public final class CoinRewardMotionManager implements MotionManager {
    public static final int SCORE_X_OFFSET_FROM_COIN_PIXELS = -4;
    public static final PopMotion BLOCK_COIN_MOTION = spinning(blockCoin(), 4, 4, SPIN_PHASE_TICKS);
    public static final PopMotion POPPED_OUT_COIN_MOTION = spinning(poppedOutCoin(), 4, 4, 0);
    public static final int BLOCK_COIN_ABOVE_PROBE_PIXELS = 24;
    public static final int BLOCK_COIN_X_OFFSET_PIXELS = 4;
    public static final int SPRITE_LINE_DELAY_PIXELS = 1;
    public static final int BLOCK_COIN_REMOVAL_SCREEN_X = 248;
    public static final int BLOCK_COIN_REMOVAL_SCREEN_Y = 197;

    static final int POPPED_OUT_COIN_X_OFFSET_PIXELS = 4;
    static final int POPPED_OUT_CAPTION_ABOVE_COIN_PIXELS = 6;
    static final int POPPED_OUT_FIRST_LADDER_INDEX = 4;

    private static final int SCREEN_MASK = 255;

    private final List<FlyingCoin> activeCoins = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScores = new ArrayList<>();
    private final List<ScorePopupAnimation> pendingScores = new ArrayList<>();

    @Override
    public void update() {
        tickScores();
        startPendingScores();
        final Iterator<FlyingCoin> coinIterator = activeCoins.iterator();
        while (coinIterator.hasNext()) {
            final FlyingCoin coin = coinIterator.next();
            final CoinPopAnimation animation = coin.getAnimation();
            if (coin.isScreenBound()) {
                followTheScreenVertically(coin);
            }
            animation.tick();
            if (animation.isExpired()) {
                coin.getLanding().accept(animation);
                remove(animation, coinIterator);
            } else if (coin.isScreenBound() && screenX(animation) >= BLOCK_COIN_REMOVAL_SCREEN_X) {
                remove(animation, coinIterator);
            }
        }
    }

    @Override
    public void updateWhileHalted() {
        tickScores();
        startPendingScores();
        final Iterator<FlyingCoin> coinIterator = activeCoins.iterator();
        while (coinIterator.hasNext()) {
            final FlyingCoin coin = coinIterator.next();
            if (!coin.isScreenBound()) {
                continue;
            }
            coin.setScreenTop(screenOf(coin.getAnimation()).top());
            if (coinY(coin.getAnimation()) >= BLOCK_COIN_REMOVAL_SCREEN_Y) {
                remove(coin.getAnimation(), coinIterator);
            }
        }
    }

    /**
     * Pops a coin out of a block.
     *
     * @param gameEngine the game engine
     * @param reward     what the caption shows and the future completes with
     * @param offset     the block's cell
     * @param probeY     the Y of the probe that hit the block, in level pixels
     * @return completed with the caption's points when the coin lands; never, if the coin leaves the screen first
     */
    public CompletableFuture<Integer> spawnCoinReward(
        final GameEngine gameEngine,
        final Reward reward,
        final Coordinates offset,
        final int probeY
    ) {
        final CoinPopAnimation coin = getBean(
            CoinPopAnimation.class,
            gameEngine,
            reward.getData(),
            offset,
            BLOCK_COIN_MOTION
        );
        final double spriteTop = probeY - BLOCK_COIN_ABOVE_PROBE_PIXELS + SPRITE_LINE_DELAY_PIXELS;
        coin.setWorldOffset(
            worldOffsetOf(gameEngine, offset.x() * TILE_SPRITE_SIZE + BLOCK_COIN_X_OFFSET_PIXELS, spriteTop)
        );
        coin.start();
        final CompletableFuture<Integer> completion = new CompletableFuture<>();
        final FlyingCoin flyingCoin = new FlyingCoin(coin, landed -> landBlockCoin(landed, completion), true);
        flyingCoin.setScreenTop(screenOf(coin).top());
        activeCoins.add(flyingCoin);
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
        activeCoins.add(new FlyingCoin(coin, landed -> landPoppedOutCoin(landed, player, objectPosition), false));
    }

    /**
     * Drops any in-flight coin pops and score captions.
     */
    @Override
    public void reset() {
        activeCoins.forEach(coin -> coin.getAnimation().detach());
        activeCoins.clear();
        activeScores.forEach(ScorePopupAnimation::detach);
        activeScores.clear();
        pendingScores.clear();
    }

    private void landBlockCoin(
        final CoinPopAnimation coin,
        final CompletableFuture<Integer> completion
    ) {
        final double coinTop = spriteTopOf(coin);
        final ScorePopupAnimation caption = caption(coin, coin.getRewardData());
        caption.placeAt(
            spriteLeftOf(coin) + SCORE_X_OFFSET_FROM_COIN_PIXELS,
            coinTop,
            caption.screenLine(coinTop - SPRITE_LINE_DELAY_PIXELS)
        );
        pendingScores.add(caption);
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
        final ScorePopupAnimation caption = caption(coin, reward.getData());
        caption.placeAt(objectPosition.x(), captionTop, caption.screenLine(captionTop));
        pendingScores.add(caption);
    }

    private ScorePopupAnimation caption(
        final CoinPopAnimation coin,
        final Reward.Data rewardData
    ) {
        return getBean(
            ScorePopupAnimation.class,
            coin.getGameEngine(),
            rewardData,
            coin.getOffset()
        );
    }

    private void startPendingScores() {
        pendingScores.forEach(ScorePopupAnimation::start);
        activeScores.addAll(pendingScores);
        pendingScores.clear();
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

    private void followTheScreenVertically(final FlyingCoin coin) {
        final double screenTop = screenOf(coin.getAnimation()).top();
        final double scrolled = screenTop - coin.getScreenTop();
        coin.setScreenTop(screenTop);
        if (scrolled != 0) {
            final WorldOffset worldOffset = coin.getAnimation().getWorldOffset();
            coin.getAnimation().setWorldOffset(worldOffset.plus(0f, (float) (-scrolled / TILE_SPRITE_SIZE), 0f));
        }
    }

    private int screenX(final CoinPopAnimation coin) {
        return (int) floor(spriteLeftOf(coin) - screenOf(coin).left()) & SCREEN_MASK;
    }

    private int coinY(final CoinPopAnimation coin) {
        return (int) floor(spriteTopOf(coin) - SPRITE_LINE_DELAY_PIXELS - screenOf(coin).top()) & SCREEN_MASK;
    }

    private static AxisAlignedBoundingBox screenOf(final CoinPopAnimation coin) {
        return coin.getGameEngine().getCameraState().getActiveObjectRegion(0);
    }

    private static double spriteLeftOf(final CoinPopAnimation coin) {
        return (double) coin.getCurrentWorldOffset().x() * TILE_SPRITE_SIZE;
    }

    private double spriteTopOf(final CoinPopAnimation coin) {
        final int rows = getLevelSceneRows(coin.getGameEngine().getLevelScene());
        return (rows - (double) coin.getCurrentWorldOffset().y()) * TILE_SPRITE_SIZE;
    }

    private void remove(
        final CoinPopAnimation coin,
        final Iterator<FlyingCoin> iterator
    ) {
        coin.detach();
        iterator.remove();
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

    @Getter
    @RequiredArgsConstructor
    static final class FlyingCoin {
        private final CoinPopAnimation animation;
        private final Consumer<CoinPopAnimation> landing;
        private final boolean screenBound;
        private double screenTop;

        void setScreenTop(final double screenTop) {
            this.screenTop = screenTop;
        }
    }
}
