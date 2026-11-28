package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.enumeration.RewardDispenseSide;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.block.animator.NoteBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.block.motion.NoteBlockBounceMotionManager;
import house.x1337.app.smb3.game.object.level.reward.Starman;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import house.x1337.app.smb3.game.object.level.reward.motion.StarmanMotionManager;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperLeafMotionManager;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.service.LevelObjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static house.x1337.app.smb3.enumeration.BlockType.NOTE_BLOCK_NO_REWARD;
import static house.x1337.app.smb3.enumeration.BlockType.NOTE_BLOCK_WITH_REWARD;
import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;
import static house.x1337.app.smb3.enumeration.ItemType.ONE_UP_MUSHROOM;
import static house.x1337.app.smb3.enumeration.SoundEffect.BLOCK;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A note block with a reward gives it up the first time it is struck: above the block when struck from below or from
 * the side, below it when landed on. Either way it bounces as a note block does and is a plain note block afterwards.
 */
class NoteBlockRewardTest {
    private static final Coordinates OFFSET = of(3, 4);

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final NoteBlockAnimator noteBlockAnimator = mock(NoteBlockAnimator.class);
    private final NoteBlockBounceMotionManager bounceManager = mock(NoteBlockBounceMotionManager.class);
    private final CoinRewardMotionManager coinRewardMotionManager = mock(CoinRewardMotionManager.class);
    private final SuperLeafMotionManager leafManager = mock(SuperLeafMotionManager.class);
    private final SuperMushroomMotionManager mushroomManager = mock(SuperMushroomMotionManager.class);
    private final LevelObjectService levelObjectService = mock(LevelObjectService.class);
    private final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final GameEngine gameEngine = mock(GameEngine.class);
    private final SuperLeaf leaf = mock(SuperLeaf.class);
    private final SuperMushroom mushroom = mock(SuperMushroom.class);
    private final StarmanMotionManager starmanManager = mock(StarmanMotionManager.class);
    private final Starman starman = mock(Starman.class);
    private final PlayerPosition position = new PlayerPosition();
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == SoundPlayer.class) {
                return soundPlayer;
            }
            if (type == NoteBlockAnimator.class) {
                return noteBlockAnimator;
            }
            if (type == NoteBlockBounceMotionManager.class) {
                return bounceManager;
            }
            if (type == CoinRewardMotionManager.class) {
                return coinRewardMotionManager;
            }
            if (type == SuperLeafMotionManager.class) {
                return leafManager;
            }
            if (type == SuperMushroomMotionManager.class) {
                return mushroomManager;
            }
            if (type == LevelObjectService.class) {
                return levelObjectService;
            }
            if (type == PowerSwitchTimeWindow.class) {
                return powerSwitchTimeWindow;
            }
            if (type == SuperLeaf.class) {
                return leaf;
            }
            if (type == SuperMushroom.class) {
                return mushroom;
            }
            if (type == StarmanMotionManager.class) {
                return starmanManager;
            }
            if (type == Starman.class) {
                return starman;
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Struck from below it bounces up and gives its leaf up above the block, once")
    void aHeadHitDispensesTheLeafAbove() {
        // Prepare
        final Block noteBlock = noteBlock("SUPER_LEAF");
        final LevelScenePlayer player = player(false);

        // Execute
        noteBlock.onCollisionFromBelow(player);
        noteBlock.onCollisionFromBelow(player);

        // Verify
        verify(bounceManager, times(2)).spawnBounceUp(gameEngine, OFFSET, noteBlockAnimator);
        verify(leafManager, times(1)).spawn(leaf);
        verify(leaf, never()).dropOutOfTheBlockBelow();
        verify(player, times(2)).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("Landed on it sinks with the player and gives its leaf up below the block")
    void landingOnItDispensesTheLeafBelow() {
        // Prepare
        final Block noteBlock = noteBlock("SUPER_LEAF");
        final LevelScenePlayer player = player(false);

        // Execute
        noteBlock.onCollisionFromAbove(player);

        // Verify
        verify(bounceManager).spawnBounceDown(gameEngine, OFFSET, noteBlockAnimator, player);
        verify(leaf).dropOutOfTheBlockBelow();
        verify(leafManager).spawn(leaf);
    }

    @ParameterizedTest
    @EnumSource(value = RewardDispenseSide.class)
    @DisplayName("A mushroom is dropped only when it comes out below the block")
    void aMushroomDropsOnlyBelow(final RewardDispenseSide side) {
        // Prepare
        final Block noteBlock = noteBlock("SUPER_MUSHROOM");
        final LevelScenePlayer player = player(false);

        // Execute
        if (side == RewardDispenseSide.BELOW) {
            noteBlock.onCollisionFromAbove(player);
        } else {
            noteBlock.onCollisionFromBelow(player);
        }

        // Verify
        verify(mushroom, times(side == RewardDispenseSide.BELOW ? 1 : 0)).dropOutOfTheBlockBelow();
        verify(mushroomManager).spawn(mushroom);
    }

    @ParameterizedTest
    @EnumSource(value = RewardDispenseSide.class)
    @DisplayName("A star is dropped only when it comes out below the block, and a small player still gets it")
    void aStarDropsOnlyBelowAndIsNotSwappedForASmallPlayer(final RewardDispenseSide side) {
        // Prepare
        final Block noteBlock = noteBlock("STARMAN");
        final LevelScenePlayer player = player(true);

        // Execute
        if (side == RewardDispenseSide.BELOW) {
            noteBlock.onCollisionFromAbove(player);
        } else {
            noteBlock.onCollisionFromBelow(player);
        }

        // Verify
        verify(starman, times(side == RewardDispenseSide.BELOW ? 1 : 0)).dropOutOfTheBlockBelow();
        verify(starmanManager).spawn(starman);
        verifyNoInteractions(mushroomManager);
    }

    @Test
    @DisplayName("A small player is given a mushroom in place of the leaf, as by any reward block")
    void aSmallPlayerGetsAMushroom() {
        // Prepare
        final Block noteBlock = noteBlock("SUPER_LEAF");
        final LevelScenePlayer player = player(true);

        // Execute
        noteBlock.onCollisionFromBelow(player);

        // Verify
        verify(mushroomManager).spawn(mushroom);
        verifyNoInteractions(leafManager);
    }

    @Test
    @DisplayName("A tail strike and a kicked shell give the reward up above the block, with the bump sound")
    void aSideStrikeDispensesAbove() {
        // Prepare
        final Block tailed = noteBlock("SUPER_LEAF");
        final Block shelled = noteBlock("SUPER_LEAF");
        final LevelScenePlayer player = player(false);

        // Execute
        tailed.onTailAttack(player);
        shelled.onKickedObjectStrike(player);

        // Verify
        verify(leafManager, times(2)).spawn(leaf);
        verify(leaf, never()).dropOutOfTheBlockBelow();
        verify(soundPlayer, times(2)).play(BLOCK);
    }

    @Test
    @DisplayName("A coin comes out above the block whichever side it is struck on")
    void aCoinIsAlwaysAbove() {
        // Prepare
        final Block fromBelow = noteBlock("COIN_SINGLE");
        final Block fromAbove = noteBlock("COIN_SINGLE");
        final LevelScenePlayer player = player(false);
        when(collisionGrid.getBumpProbeY()).thenReturn(0);
        when(coinRewardMotionManager.spawnCoinReward(any(), any(), any(), anyInt()))
            .thenReturn(new CompletableFuture<>());

        // Execute
        fromBelow.onCollisionFromBelow(player);
        fromAbove.onCollisionFromAbove(player);

        // Verify
        verify(coinRewardMotionManager, times(2)).spawnCoinReward(eq(gameEngine), any(), eq(OFFSET), eq(0));
    }

    @Test
    @DisplayName("Once it has given its reward up it is a plain note block, which bounces and gives nothing")
    void itIsAPlainNoteBlockAfterwards() {
        // Prepare
        final Block noteBlock = noteBlock("SUPER_LEAF");
        final LevelScenePlayer player = player(false);

        // Execute
        noteBlock.onCollisionFromBelow(player);

        // Verify
        assertThat(noteBlock.getBlockType()).isEqualTo(NOTE_BLOCK_NO_REWARD);
        assertThat(noteBlock.getType()).isEqualTo(LevelObjectTypeSingleTiled.NOTE_BLOCK_NO_REWARD);
        assertThat(noteBlock.getReward()).isNull();
        assertThat(noteBlock.getAnimator()).isSameAs(noteBlockAnimator);
        assertThat(noteBlock.isCollidable()).isTrue();
    }

    @Test
    @DisplayName("It stays a note block, replaced by nothing, so no empty block is placed and no tile is erased")
    void itIsNeverEmptied() {
        // Prepare
        final Block noteBlock = noteBlock("SUPER_LEAF");

        // Execute
        noteBlock.onCollisionFromBelow(player(false));

        // Verify
        verifyNoInteractions(levelObjectService, collisionGrid);
    }

    @Test
    @DisplayName("Its reward is configured as that of a question block is")
    void itIsConfiguredWithItsReward() {
        // Prepare & Execute
        final Block oneUp = noteBlock("ONE_UP_MUSHROOM");
        final Block unconfigured = noteBlockWithoutReward();

        // Verify
        assertThat(oneUp.getReward()).isEqualTo(ONE_UP_MUSHROOM);
        assertThat(oneUp.getBlockType()).isEqualTo(NOTE_BLOCK_WITH_REWARD);
        assertThat(unconfigured.getReward()).as("A coin, by default").isEqualTo(COIN_SINGLE);
    }

    private Block noteBlock(final String reward) {
        final Block block = new Block(gameEngine, mock(ImageResource.class), OFFSET);
        block.configure(new LevelObjectData(Map.of("blockType", "NOTE_BLOCK_WITH_REWARD", "reward", reward)));
        return block;
    }

    private Block noteBlockWithoutReward() {
        final Block block = new Block(gameEngine, mock(ImageResource.class), OFFSET);
        block.configure(new LevelObjectData(Map.of("blockType", "NOTE_BLOCK_WITH_REWARD")));
        return block;
    }

    private LevelScenePlayer player(final boolean small) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPlayerData()).thenReturn(new PlayerData());
        when(player.getGameEngine()).thenReturn(gameEngine);
        when(player.getCollisionGrid()).thenReturn(collisionGrid);
        when(player.isSmall()).thenReturn(small);
        return player;
    }
}