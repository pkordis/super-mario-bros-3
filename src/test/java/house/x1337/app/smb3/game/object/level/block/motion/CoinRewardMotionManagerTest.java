package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.block.animation.CoinPopAnimation;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_2000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_4000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_8000;
import static house.x1337.app.smb3.enumeration.SoundEffect.COIN;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_MOTION;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.POPPED_OUT_COIN_MOTION;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two coins that pop up and land with a caption: the block coin and the coin the card touch pops out of an
 * object. The popped-out coin lands as a coin and a score from the kill ladder, one step higher with each landing,
 * so coins landing together climb the kill ladder from 1000.
 *
 * <p>The screen is 256 x 240 with its top-left at {@link #screen}, which a test moves to scroll it.
 */
class CoinRewardMotionManagerTest {
    private static final int ROWS = 30;
    private static final double TOLERANCE = 1e-5;
    private static final double SCREEN_LEFT = 0;
    private static final double SCREEN_TOP = 160;

    /** The block in cell (5, 20), its top at level Y 320: the hit's head probe is 14 px into it. */
    private static final int BLOCK_COLUMN = 5;
    private static final int BLOCK_ROW = 20;
    private static final int PROBE_Y = BLOCK_ROW * 16 + 14;

    private final GameEngine gameEngine = mock(GameEngine.class);
    private final PlayerData playerData = new PlayerData();
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
    private final AtomicReference<AxisAlignedBoundingBox> screen = new AtomicReference<>();
    private final List<CoinPopAnimation> coins = new ArrayList<>();
    private final List<PopMotion> coinMotions = new ArrayList<>();
    private final List<ScorePopupAnimation> captions = new ArrayList<>();
    private final List<Reward.Data> captionRewards = new ArrayList<>();
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelScenePlayer player;
    private CoinRewardMotionManager manager;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(64, ROWS));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        final CameraState cameraState = mock(CameraState.class);
        scrollTo(SCREEN_LEFT, SCREEN_TOP);
        when(cameraState.getActiveObjectRegion(anyInt())).thenAnswer(invocation -> screen.get());
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        player = mock(LevelScenePlayer.class);
        when(player.getGameEngine()).thenReturn(gameEngine);
        when(player.getPlayerData()).thenReturn(playerData);
        when(player.getRuntimeState()).thenReturn(runtimeState);

        // The coin's flight is the real one it is given; its sprite is not
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == CoinPopAnimation.class) {
                return coin(invocation.getArgument(2), invocation.getArgument(4));
            }
            if (type == ScorePopupAnimation.class) {
                return caption(invocation.getArgument(2));
            }
            if (type == Reward.Data.class) {
                return new Reward.Data();
            }
            if (type == SoundPlayer.class) {
                return soundPlayer;
            }
            return mock(type);
        });
        manager = new CoinRewardMotionManager();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A block coin launches 24 px above the probe that hit the block, drawn a line down, 4 px in")
    void aBlockCoinLaunchesAboveTheProbe() {
        // Execute
        spawnBlockCoin();

        // Verify
        assertThat(coinMotions).containsExactly(BLOCK_COIN_MOTION);
        verify(coins.getFirst()).start();
        assertThat(spriteLeftOf(coins.getFirst())).isCloseTo(BLOCK_COLUMN * 16 + 4, within(TOLERANCE));
        assertThat(spriteTopOf(coins.getFirst())).isCloseTo(PROBE_Y - 24 + 1, within(TOLERANCE));
    }

    @Test
    @DisplayName("A block coin's 39th update removes it and leaves a 100 at the coin's Y, 4 px left")
    void aBlockCoinLands() {
        // Prepare
        final CompletableFuture<Integer> points = spawnBlockCoin();

        // Execute
        tick(38);
        assertThat(captions).as("Still in flight").isEmpty();
        tick(1);

        // Verify - the coin's Y is the line above where the coin is drawn
        final double coinTop = PROBE_Y - 24 + 1 - 15;
        assertThat(points).isCompletedWithValue(100);
        assertThat(captionRewards).containsExactly(SCORE_100.getData());
        verify(captions.getFirst()).placeAt(BLOCK_COLUMN * 16 + 4 - 4, coinTop, (int) (coinTop - 1 - SCREEN_TOP));
        verify(coins.getFirst()).detach();
        assertThat(manager.getActiveCoins()).isEmpty();
        assertThat(playerData.getCoins()).as("A block gives its coin at the hit, not here").isZero();
    }

    @Test
    @DisplayName("A coin's caption gets its first update, and shows, on the frame after the coin's last")
    void aCoinCaptionStartsOnTheNextFrame() {
        // Prepare
        spawnBlockCoin();
        tick(39);
        final ScorePopupAnimation caption = captions.getFirst();
        verify(caption, never()).start();

        // Execute
        tick(1);

        // Verify - start() gives it that first update itself
        verify(caption).start();
        verify(caption, never()).tick();
        assertThat(manager.getActiveScores()).containsExactly(caption);
        assertThat(manager.getPendingScores()).isEmpty();

        // Execute & Verify - from then on it rises with the others
        tick(1);
        verify(caption).tick();
    }

    @Test
    @DisplayName("A block coin at screen X 248 or more is removed, with no caption and no points")
    void aBlockCoinOffTheRightIsRemoved() {
        // Prepare - the coin's left is X 84
        final CompletableFuture<Integer> points = spawnBlockCoin();
        tick(1);

        // Execute & Verify - at screen X 247 it stays
        scrollTo(84 - 247, SCREEN_TOP);
        tick(1);
        assertThat(manager.getActiveCoins()).hasSize(1);

        // Execute & Verify - at 248 it goes
        scrollTo(84 - 248, SCREEN_TOP);
        tick(1);
        assertThat(manager.getActiveCoins()).isEmpty();
        verify(coins.getFirst()).detach();
        tick(40);
        assertThat(captions).isEmpty();
        assertThat(points).isNotDone();
    }

    @Test
    @DisplayName("A block coin past the screen's left wraps to screen X 255 and is removed too")
    void aBlockCoinOffTheLeftIsRemoved() {
        // Prepare
        spawnBlockCoin();

        // Execute
        scrollTo(85, SCREEN_TOP);
        tick(1);

        // Verify
        assertThat(manager.getActiveCoins()).isEmpty();
    }

    @Test
    @DisplayName("The update that ends a block coin leaves its caption wherever the coin is")
    void theLastUpdateLandsAnywhere() {
        // Prepare
        final CompletableFuture<Integer> points = spawnBlockCoin();
        tick(38);

        // Execute - the removal by velocity comes before the screen X test
        scrollTo(84 - 250, SCREEN_TOP);
        tick(1);

        // Verify
        assertThat(points).isCompletedWithValue(100);
        assertThat(captions).hasSize(1);
    }

    @Test
    @DisplayName("During gameplay a block coin moves with a vertical scroll, keeping its place on the screen")
    void aBlockCoinMovesWithTheScreen() {
        // Prepare
        spawnBlockCoin();
        tick(1);
        final double before = spriteTopOf(coins.getFirst());

        // Execute - the screen goes 3 px up, then 2 px down
        scrollTo(SCREEN_LEFT, SCREEN_TOP - 3);
        tick(1);
        final double afterUp = spriteTopOf(coins.getFirst());
        scrollTo(SCREEN_LEFT, SCREEN_TOP - 1);
        tick(1);

        // Verify - its own flight (5 px, then 5 px up) plus the scroll
        assertThat(afterUp).isCloseTo(before - 5 - 3, within(TOLERANCE));
        assertThat(spriteTopOf(coins.getFirst())).isCloseTo(before - 10 - 1, within(TOLERANCE));
    }

    @Test
    @DisplayName("While gameplay is halted a block coin stays put in the level, and does not jump when it resumes")
    void aHaltedBlockCoinStaysPut() {
        // Prepare
        spawnBlockCoin();
        tick(1);
        final double before = spriteTopOf(coins.getFirst());

        // Execute
        scrollTo(SCREEN_LEFT, SCREEN_TOP - 4);
        manager.updateWhileHalted();
        final double whileHalted = spriteTopOf(coins.getFirst());
        tick(1);

        // Verify
        assertThat(whileHalted).isEqualTo(before);
        assertThat(spriteTopOf(coins.getFirst())).as("Its own 5 px only").isCloseTo(before - 5, within(TOLERANCE));
    }

    @Test
    @DisplayName("While gameplay is halted a block coin at screen Y 197 or more is removed")
    void aHaltedBlockCoinAboveTheScreenIsRemoved() {
        // Prepare - after 19 updates the coin is 55 px up, so its screen Y = PROBE_Y - 24 - 55 - the screen's top
        spawnBlockCoin();
        tick(19);

        // Execute & Verify - at 196 it stays
        scrollTo(SCREEN_LEFT, PROBE_Y - 24 - 55 - 196.0);
        manager.updateWhileHalted();
        assertThat(manager.getActiveCoins()).hasSize(1);

        // Execute & Verify - at 197 it goes
        scrollTo(SCREEN_LEFT, PROBE_Y - 24 - 55 - 197.0);
        manager.updateWhileHalted();
        assertThat(manager.getActiveCoins()).isEmpty();
    }

    @Test
    @DisplayName("While gameplay is halted a block coin above the screen's top, its Y wrapped, is removed")
    void aHaltedBlockCoinAboveTheTopIsRemoved() {
        // Prepare
        spawnBlockCoin();
        tick(19);

        // Execute - the coin's screen Y is -1, so 255
        scrollTo(SCREEN_LEFT, PROBE_Y - 24 - 55 + 1.0);
        manager.updateWhileHalted();

        // Verify
        assertThat(manager.getActiveCoins()).isEmpty();
    }

    @Test
    @DisplayName("A popped-out coin flies its own arc, 4 px right of the object, from the object's top")
    void aPoppedOutCoinStartsAtTheObject() {
        // Execute
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));

        // Verify
        assertThat(coinMotions).containsExactly(POPPED_OUT_COIN_MOTION);
        verify(coins.getFirst()).start();
        assertThat(spriteLeftOf(coins.getFirst())).isCloseTo(164, within(TOLERANCE));
        assertThat(spriteTopOf(coins.getFirst())).isCloseTo(300, within(TOLERANCE));
    }

    @Test
    @DisplayName("A popped-out coin plays the coin sound once, as it pops out")
    void aPoppedOutCoinPlaysTheCoinSound() {
        // Execute
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));
        tick(36);

        // Verify
        verify(manager.getSoundPlayer(), times(1)).play(COIN);
        verify(manager.getSoundPlayer(), never()).play(SoundEffect.ONE_UP);
    }

    @Test
    @DisplayName("A popped-out coin that is the hundredth plays the extra-life sound as it lands")
    void theHundredthPoppedOutCoinPlaysTheExtraLifeSound() {
        // Prepare
        playerData.setCoins(99);
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));

        // Execute
        tick(36);

        // Verify
        verify(manager.getSoundPlayer(), times(1)).play(SoundEffect.ONE_UP);
    }

    @Test
    @DisplayName("A popped-out coin lands after 36 ticks, giving a coin and 1000 points with a caption")
    void aPoppedOutCoinLands() {
        // Prepare
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));

        // Execute & Verify
        tick(35);
        assertThat(playerData.getCoins()).as("In flight").isZero();
        assertThat(captions).isEmpty();
        tick(1);
        assertThat(playerData.getCoins()).isEqualTo(1);
        assertThat(playerData.getScore()).isEqualTo(1000);
        assertThat(captionRewards).containsExactly(SCORE_1000.getData());
        verify(coins.getFirst()).detach();
        assertThat(manager.getActiveCoins()).isEmpty();
    }

    @Test
    @DisplayName("The popped-out coin's caption is placed 6 px above the landed coin, at the object's X")
    void thePoppedOutCaptionIsAboveTheCoin() {
        // Prepare
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));

        // Execute
        tick(36);

        // Verify - the coin ends 12 px up, at Y 288
        verify(captions.getFirst()).placeAt(160, 282, (int) (282 - SCREEN_TOP));
    }

    @Test
    @DisplayName("A popped-out coin is not screen-bound: it lands even off the screen")
    void aPoppedOutCoinOffTheScreenLands() {
        // Prepare
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));
        scrollTo(400, SCREEN_TOP);

        // Execute
        tick(36);

        // Verify
        assertThat(captions).hasSize(1);
    }

    @Test
    @DisplayName("Popped-out coins landing together climb the kill ladder: 1000, 2000, 4000, 8000, then a 1UP")
    void poppedOutCoinsClimbTheLadder() {
        // Prepare
        for (int coin = 0; coin < 5; coin++) {
            manager.spawnPoppedOutCoin(player, new PixelPosition(32 + 16 * coin, 300));
        }
        final int lives = playerData.getLives();

        // Execute
        tick(36);

        // Verify
        assertThat(captionRewards).containsExactly(
            SCORE_1000.getData(),
            SCORE_2000.getData(),
            SCORE_4000.getData(),
            SCORE_8000.getData(),
            ONE_UP.getData()
        );
        assertThat(playerData.getCoins()).isEqualTo(5);
        assertThat(playerData.getScore()).isEqualTo(15_000);
        assertThat(playerData.getLives()).isEqualTo(lives + 1);
    }

    @Test
    @DisplayName("While gameplay is halted the coins stand still and the captions keep rising")
    void haltedCoinsStandStill() {
        // Prepare - one caption in flight, one coin still up
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));
        tick(37);
        spawnBlockCoin();

        // Execute
        manager.updateWhileHalted();

        // Verify - the caption started on the tick after its coin, and rose once more now
        verify(coins.get(1), never()).tick();
        verify(captions.getFirst()).start();
        verify(captions.getFirst(), times(1)).tick();
        assertThat(manager.getActiveCoins()).hasSize(1);
    }

    @Test
    @DisplayName("A level reset drops the coins, the captions in flight and the ones still to start")
    void aResetDropsEverything() {
        // Prepare - a caption in flight, one still to start and a coin still up
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 300));
        tick(37);
        manager.spawnPoppedOutCoin(player, new PixelPosition(200, 300));
        tick(36);
        spawnBlockCoin();

        // Execute
        manager.reset();

        // Verify
        verify(coins.get(2)).detach();
        verify(captions.getFirst()).detach();
        verify(captions.get(1), never()).start();
        assertThat(manager.getActiveCoins()).isEmpty();
        assertThat(manager.getActiveScores()).isEmpty();
        assertThat(manager.getPendingScores()).isEmpty();
    }

    private CompletableFuture<Integer> spawnBlockCoin() {
        return manager.spawnCoinReward(gameEngine, SCORE_100, of(BLOCK_COLUMN, BLOCK_ROW), PROBE_Y);
    }

    private void scrollTo(final double left, final double top) {
        screen.set(new AxisAlignedBoundingBox(left, top, left + 256, top + 240));
    }

    /** A coin that keeps the world offset it is given and flies its real motion over the ticks it gets. */
    private CoinPopAnimation coin(final Reward.Data rewardData, final PopMotion motion) {
        final CoinPopAnimation coin = mock(CoinPopAnimation.class);
        final AtomicInteger ticks = new AtomicInteger();
        final AtomicReference<WorldOffset> worldOffset = new AtomicReference<>();
        doAnswer(invocation -> ticks.incrementAndGet()).when(coin).tick();
        doAnswer(invocation -> {
            worldOffset.set(invocation.getArgument(0));
            return null;
        }).when(coin).setWorldOffset(any());
        when(coin.getWorldOffset()).thenAnswer(invocation -> worldOffset.get());
        when(coin.getCurrentWorldOffset()).thenAnswer(invocation -> worldOffset.get()
            .plus(0f, motion.verticalOffsetAt(ticks.get()) / 16f, 0f));
        when(coin.isExpired()).thenAnswer(invocation -> ticks.get() >= motion.durationTicks());
        when(coin.getMotion()).thenReturn(motion);
        when(coin.getDimensions()).thenReturn(halfTileWidth("Coin"));
        when(coin.getRewardData()).thenReturn(rewardData);
        when(coin.getGameEngine()).thenReturn(gameEngine);
        coins.add(coin);
        coinMotions.add(motion);
        return coin;
    }

    /** A caption that measures screen lines as the real one does. */
    private ScorePopupAnimation caption(final Reward.Data rewardData) {
        final ScorePopupAnimation caption = mock(ScorePopupAnimation.class);
        when(caption.getRewardData()).thenReturn(rewardData);
        when(caption.screenLine(anyDouble()))
            .thenAnswer(invocation -> (int) Math.floor((double) invocation.getArgument(0) - screen.get().top()));
        captions.add(caption);
        captionRewards.add(rewardData);
        return caption;
    }

    private static double spriteLeftOf(final CoinPopAnimation coin) {
        return coin.getCurrentWorldOffset().x() * 16.0;
    }

    private static double spriteTopOf(final CoinPopAnimation coin) {
        return (ROWS - (double) coin.getCurrentWorldOffset().y()) * 16;
    }

    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            manager.update();
        }
    }
}
