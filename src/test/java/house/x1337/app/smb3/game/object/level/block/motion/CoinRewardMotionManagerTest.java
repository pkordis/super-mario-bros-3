package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.block.animation.CoinPopAnimation;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_2000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_4000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_8000;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.BLOCK_COIN_MOTION;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.POPPED_OUT_COIN_MOTION;
import static house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager.SCORE_X_OFFSET_FROM_COIN;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two coins that pop up and land with a caption: the block coin ({@code CoinPUp_*}) and the coin the card touch
 * pops out of an object ({@code SOBJ_POPPEDOUTCOIN}). The popped-out coin lands as {@code SObj_CoinOrDebris} lands it
 * (dasm prg007.asm:3506-3521): a coin, and the score {@code $89 + Kill_Tally} with {@code Kill_Tally} going up
 * (prg007.asm:4036-4040), so coins landing together climb the kill ladder from 1000.
 */
class CoinRewardMotionManagerTest {
    private static final int ROWS = 12;
    private static final float TOLERANCE = 1e-6f;
    private static final WorldOffset BLOCK_COIN_AT_LANDING = WorldOffset.of(5.25f, 9f, 0.06f);

    private final GameEngine gameEngine = mock(GameEngine.class);
    private final PlayerData playerData = new PlayerData();
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
    private final List<CoinPopAnimation> coins = new ArrayList<>();
    private final List<PopMotion> coinMotions = new ArrayList<>();
    private final List<ScorePopupAnimation> captions = new ArrayList<>();
    private final List<Reward.Data> captionRewards = new ArrayList<>();
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelScenePlayer player;
    private CoinRewardMotionManager manager;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(64, ROWS));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
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
                final ScorePopupAnimation caption = mock(ScorePopupAnimation.class);
                final Reward.Data rewardData = invocation.getArgument(2);
                when(caption.getRewardData()).thenReturn(rewardData);
                captions.add(caption);
                captionRewards.add(rewardData);
                return caption;
            }
            if (type == Reward.Data.class) {
                return new Reward.Data();
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
    @DisplayName("A block coin flies from the block's top, 4 px in, and lands with a 100 at its Y, 4 px left")
    void aBlockCoinLands() {
        // Prepare
        final CompletableFuture<Integer> points = manager.spawnCoinReward(gameEngine, SCORE_100, of(5, 3));

        // Execute
        tick(BLOCK_COIN_MOTION.durationTicks());

        // Verify
        assertThat(coinMotions).containsExactly(BLOCK_COIN_MOTION);
        final WorldOffset start = worldOffsetOf(coins.getFirst());
        assertThat(start.x()).isCloseTo(5.25f, within(TOLERANCE));
        assertThat(start.y()).as("The block's top").isCloseTo(ROWS - 3f, within(TOLERANCE));
        assertThat(points).isCompletedWithValue(100);
        assertThat(captionRewards).containsExactly(SCORE_100.getData());
        final WorldOffset caption = worldOffsetOf(captions.getFirst());
        assertThat(caption.x()).isCloseTo(BLOCK_COIN_AT_LANDING.x() + SCORE_X_OFFSET_FROM_COIN, within(TOLERANCE));
        assertThat(caption.y()).as("Scores_Y = CoinPUp_Y").isCloseTo(BLOCK_COIN_AT_LANDING.y(), within(TOLERANCE));
        assertThat(playerData.getCoins()).as("A block gives its coin at the hit, not here").isZero();
    }

    @Test
    @DisplayName("A popped-out coin flies its own arc, 4 px right of the object, from the object's top")
    void aPoppedOutCoinStartsAtTheObject() {
        // Execute
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 96));

        // Verify
        assertThat(coinMotions).containsExactly(POPPED_OUT_COIN_MOTION);
        verify(coins.getFirst()).start();
        final WorldOffset start = worldOffsetOf(coins.getFirst());
        assertThat(start.x()).isCloseTo(164f / 16, within(TOLERANCE));
        assertThat(start.y()).isCloseTo(ROWS - 96f / 16, within(TOLERANCE));
    }

    @Test
    @DisplayName("A popped-out coin lands after 36 ticks, giving a coin and 1000 points with a caption")
    void aPoppedOutCoinLands() {
        // Prepare
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 96));

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
    @DisplayName("The popped-out coin's caption starts 6 px above the landed coin, at the object's X")
    void thePoppedOutCaptionIsAboveTheCoin() {
        // Prepare
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 96));

        // Execute
        tick(36);

        // Verify - the coin ends 12 px up, at y 84; the caption's top is at y 78, plus the caption's 4 px dip
        final WorldOffset caption = worldOffsetOf(captions.getFirst());
        verify(captions.getFirst()).start();
        assertThat(caption.x()).isCloseTo(160f / 16, within(TOLERANCE));
        assertThat(caption.y()).isCloseTo(ROWS - 78f / 16 + 4f / 16, within(TOLERANCE));
    }

    @Test
    @DisplayName("Popped-out coins landing together climb the kill ladder: 1000, 2000, 4000, 8000, then a 1UP")
    void poppedOutCoinsClimbTheLadder() {
        // Prepare
        for (int coin = 0; coin < 5; coin++) {
            manager.spawnPoppedOutCoin(player, new PixelPosition(32 + 16 * coin, 96));
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
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 96));
        tick(36);
        manager.spawnCoinReward(gameEngine, SCORE_100, of(5, 3));

        // Execute
        manager.updateWhileHalted();

        // Verify - the caption rose once on the tick it appeared, and once more now
        verify(coins.get(1), never()).tick();
        verify(captions.getFirst(), times(2)).tick();
        assertThat(manager.getActiveCoins()).hasSize(1);
    }

    @Test
    @DisplayName("A level reset drops the coins and the captions in flight")
    void aResetDropsEverything() {
        // Prepare
        manager.spawnPoppedOutCoin(player, new PixelPosition(160, 96));
        tick(36);
        manager.spawnCoinReward(gameEngine, SCORE_100, of(5, 3));

        // Execute
        manager.reset();

        // Verify
        verify(coins.get(1)).detach();
        verify(captions.getFirst()).detach();
        assertThat(manager.getActiveCoins()).isEmpty();
        assertThat(manager.getActiveScores()).isEmpty();
    }

    private CoinPopAnimation coin(final Reward.Data rewardData, final PopMotion motion) {
        final CoinPopAnimation coin = mock(CoinPopAnimation.class);
        final AtomicInteger ticks = new AtomicInteger();
        doAnswer(invocation -> ticks.incrementAndGet()).when(coin).tick();
        when(coin.isExpired()).thenAnswer(invocation -> ticks.get() >= motion.durationTicks());
        when(coin.getMotion()).thenReturn(motion);
        when(coin.getDimensions()).thenReturn(halfTileWidth("Coin"));
        when(coin.getRewardData()).thenReturn(rewardData);
        when(coin.getGameEngine()).thenReturn(gameEngine);
        when(coin.getCurrentWorldOffset()).thenReturn(BLOCK_COIN_AT_LANDING);
        coins.add(coin);
        coinMotions.add(motion);
        return coin;
    }

    private static WorldOffset worldOffsetOf(final CoinPopAnimation coin) {
        final ArgumentCaptor<WorldOffset> offset = ArgumentCaptor.forClass(WorldOffset.class);
        verify(coin).setWorldOffset(offset.capture());
        return offset.getValue();
    }

    private static WorldOffset worldOffsetOf(final ScorePopupAnimation caption) {
        final ArgumentCaptor<WorldOffset> offset = ArgumentCaptor.forClass(WorldOffset.class);
        verify(caption).setWorldOffset(offset.capture());
        return offset.getValue();
    }

    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            manager.update();
        }
    }
}
