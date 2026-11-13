package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.Message;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.FireworksPicture;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.LivesBonusPicture;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCardMotionManager;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCardSpawner;
import house.x1337.app.smb3.game.object.level.effect.PoofMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballMotionManager;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.PlayerTimer;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEvent;
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.event.LevelCompleted;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.enumeration.SoundEffect.CASINO_BEEP;
import static house.x1337.app.smb3.enumeration.SoundEffect.ONE_UP;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.COURSE_CLEAR_MESSAGE_TICK;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.COURSE_CLEAR_TICKS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.EXIT_TICKS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FANFARE_LIVES_BONUS_X;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FANFARE_MESSAGE_TICK;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FANFARE_MESSAGE_TICKS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard.LARGE_TWINKLE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard.SMALL_TWINKLE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.LIVES_BONUS_X;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.CARD_FLYING_OFF;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.COMPLETED;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.COURSE_CLEAR;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.EXITING;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.FANFARE_APPEARING;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.FANFARE_MESSAGE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.FANFARE_RISING;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.PAYING_OUT_LIVES;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.TWINKLE_TICKS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.YOU_GOT_A_CARD_TICK;
import static house.x1337.app.smb3.game.level.completion.StatusBarCards.FLASH_BIT;
import static house.x1337.app.smb3.game.level.completion.StatusBarCards.NEWEST_CARD_HOLD_TICKS;
import static house.x1337.app.smb3.game.player.PlayerData.CARD_SLOTS;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Touching the card starts the level's completion: the card goes into the player's next slot, the clock stops, and
 * the P-switch window, the kill chain and the player's vertical velocity are cleared. Then the card flies off, the
 * clock turns into score and the level ends on the state timers. Three cards pay out lives first, and three matching
 * cards play the fanfare before that: in a {@value #TALL_ROWS}-row level, with the card at row 21, the view reaches
 * the sky on tick {@value #SKY_TICKS} of the fanfare.
 */
class LevelCompletionTest {
    /** The card at row 9 (y 144) reaches y -19 after 65 ticks, so the screen top at 0 sees it gone on tick 66. */
    private static final int FLIGHT_TICKS = 66;

    /** Sprite X 32 once wrapped round, on a screen whose left edge is at 0. */
    private static final double EXIT_X = 288;

    private static final int ROWS = 12;
    private static final int SKY_TICKS = 100;
    private static final int TALL_ROWS = 27;

    private final PlayerData playerData = new PlayerData();
    private final PlayerPosition position = new PlayerPosition();
    private final PlayerRuntimeState runtimeState = mock(PlayerRuntimeState.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final GameObjectAnimator<?> tileAnimator = mock(GameObjectAnimator.class);
    private final MotionManager otherManager = mock(MotionManager.class);
    private final LevelScenePlayer player = mock(LevelScenePlayer.class);
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final LevelFrameCounter frameCounter = new LevelFrameCounter();
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private LevelScene levelScene;
    private LevelCompletion levelCompletion;
    private int musicTicks;

    @BeforeEach
    void prepare() {
        // LevelCompletion takes its card spawner from the bean factory; a real one reads the scene's card.
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(SoundPlayer.class)).thenReturn(soundPlayer);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelCompletionCardSpawner.class), any(GameEngine.class)))
            .thenAnswer(invocation -> new LevelCompletionCardSpawner(invocation.<GameEngine>getArgument(1)));
        gameEngine = mock(GameEngine.class);
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.getPowerSwitchTimeWindow()).thenReturn(powerSwitchTimeWindow);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        doReturn(List.of(tileAnimator, otherManager)).when(gameEngine).getMotionManagers();
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getLevelCompletionCard()).thenReturn(of(30, 9));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(64, ROWS));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        this.levelScene = levelScene;
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt())).thenReturn(new AxisAlignedBoundingBox(0, 0, 256, 192));
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getLevelFrameCounter()).thenReturn(frameCounter);

        when(player.getPlayerData()).thenReturn(playerData);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);

        // The course-clear jingle ends at once unless a test sets how many ticks it plays for
        levelCompletion = new LevelCompletion(gameEngine);
        levelCompletion.reset();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The touch puts the face showing into the next status-bar slot")
    void theCardGoesIntoTheNextSlot() {
        // Prepare
        playerData.addCard(STAR);

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        assertThat(playerData.getCards()).containsExactly(STAR, FLOWER, null);
        assertThat(levelCompletion.isUnderway()).isTrue();
        assertThat(levelCompletion.getCollectedCard()).isEqualTo(FLOWER);
    }

    @Test
    @DisplayName("The touch stops the clock, which stays on the status bar showing the same time")
    void theClockStops() {
        // Prepare - 200 seconds in
        final PlayerTimer timer = playerData.getPlayerTimer();
        timer.setInitialTime(200);
        timer.start();

        // Execute
        levelCompletion.collect(FLOWER, player);
        for (int tick = 0; tick < 3 * SIMULATION_FPS; tick++) {
            timer.tick();
        }

        // Verify
        assertThat(timer.isActive()).as("Not counting").isFalse();
        assertThat(timer.getTime()).as("Three seconds later").isEqualTo(200);
        assertThat(playerData.isTimerShown()).as("Still on the status bar").isTrue();
    }

    @Test
    @DisplayName("The touch freezes the tile animators and closes the P-switch window")
    void theTileAnimationsAndThePowerSwitchStop() {
        // Prepare
        powerSwitchTimeWindow.activate();

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        verify(tileAnimator).freeze();
        assertThat(powerSwitchTimeWindow.isActive()).isFalse();
    }

    @Test
    @DisplayName("The touch resets the kill chain and the player's vertical velocity")
    void theKillChainAndTheFallStop() {
        // Prepare
        position.setDY(2.5);

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        verify(runtimeState).setKillTally(0);
        assertThat(position.getDY()).isZero();
    }

    @Test
    @DisplayName("Only the first touch of a run counts, and LevelCardCollected is published once")
    void onlyTheFirstTouchCounts() {
        // Execute
        levelCompletion.collect(FLOWER, player);
        levelCompletion.collect(STAR, player);

        // Verify
        verify(gameEngine, times(1)).publish(any(LevelCardCollected.class));
        verify(gameEngine).publish(new LevelCardCollected(FLOWER, false));
        assertThat(playerData.getCards()).containsExactly(FLOWER, null, null);
        assertThat(levelCompletion.getCollectedCard()).isEqualTo(FLOWER);
    }

    @Test
    @DisplayName("A collected card is not placed again, and a new run starts with the card uncollected")
    void aNewRunStartsOver() {
        // Prepare
        levelCompletion.collect(FLOWER, player);

        // Execute
        final boolean underwayBefore = levelCompletion.isUnderway();
        levelCompletion.reset();

        // Verify
        assertThat(underwayBefore).isTrue();
        assertThat(levelCompletion.isUnderway()).isFalse();
        assertThat(levelCompletion.getCollectedCard()).isNull();
    }

    @Test
    @DisplayName("The touch starts the player's run-off")
    void theTouchStartsTheRunOff() {
        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        verify(player).startRunningOff();
    }

    @Test
    @DisplayName("The card flies off first: 3, 2, 3, 2 px a tick until its bottom is above the top of the screen")
    void theCardFliesOffFirst() {
        // Prepare - the card at y 144 (row 9), the screen's top at 0; no time to convert
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);

        // Execute
        final int flightTicks = ticksUntil(COURSE_CLEAR);

        // Verify - after 65 ticks the card is at y -19, so its bottom (-3) is above the top; seen on tick 66
        assertThat(flightTicks).isEqualTo(FLIGHT_TICKS);
        assertThat(levelCompletion.getPhaseTimer()).isEqualTo(COURSE_CLEAR_TICKS);
    }

    @Test
    @DisplayName("Once the card is off screen the clock turns into score, one step a tick, while the HUD shows it")
    void theClockTurnsIntoScoreAfterTheFlight() {
        // Prepare
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        tick(FLIGHT_TICKS);
        assertThat(playerData.getTimer()).as("Nothing during the flight").isEqualTo(287);

        // Execute & Verify
        tick(1);
        assertThat(playerData.getTimer()).as("First step").isEqualTo(277);
        assertThat(playerData.getScore()).isEqualTo(500);
        tick(34);
        assertThat(playerData.getTimer()).as("35 steps").isZero();
        assertThat(playerData.getScore()).isEqualTo(14_350);
        assertThat(playerData.isTimerShown()).as("000 on the status bar").isTrue();
    }

    @Test
    @DisplayName("Nothing is converted while the course-clear jingle plays")
    void theBonusWaitsForTheMusic() {
        // Prepare - a jingle that ends on the 200th tick
        musicTicks = 200;
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);

        // Execute & Verify
        tick(199);
        assertThat(levelCompletion.getPhase()).isEqualTo(COURSE_CLEAR);
        assertThat(playerData.getTimer()).as("Still playing").isEqualTo(287);
        assertThat(playerData.getScore()).isZero();
        tick(1);
        assertThat(playerData.getTimer()).as("The jingle has ended").isEqualTo(277);
    }

    @Test
    @DisplayName("Only the course-clear jingle's end lets the bonus start, and only after the card is touched")
    void onlyTheCourseClearJinglesEndCounts() {
        // Prepare - the jingle's end heard before the touch, then the touch
        levelCompletion.onSoundPlayComplete(new SoundPlayComplete(MusicTrack.COURSE_CLEAR));
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);

        // Execute & Verify
        levelCompletion.onSoundPlayComplete(new SoundPlayComplete(MusicTrack.HURRY_UP));
        for (int tick = 0; tick < 300; tick++) {
            levelCompletion.tick();
        }
        assertThat(playerData.getTimer()).as("Still waiting for the jingle").isEqualTo(287);
        levelCompletion.onSoundPlayComplete(new SoundPlayComplete(MusicTrack.COURSE_CLEAR));
        levelCompletion.tick();
        assertThat(playerData.getTimer()).as("The jingle has ended").isEqualTo(277);
    }

    @Test
    @DisplayName("Each step of the bonus beeps once, and the music wait and the end of the clock are silent")
    void eachStepBeepsOnce() {
        // Prepare
        musicTicks = 50;
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);

        // Execute
        tick(49);
        verify(soundPlayer, never()).play(CASINO_BEEP);
        tick(400);

        // Verify
        assertThat(playerData.getTimer()).isZero();
        verify(soundPlayer, times(35)).play(CASINO_BEEP);
    }

    @Test
    @DisplayName("The exit waits for the course-clear timer, then the clock, then the player, in turn")
    void theExitWaitsForEachCondition() {
        // Execute & Verify - the timer: the clock is done long before its 96 ticks
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        assertThat(ticksUntil(EXITING)).as("The timer").isEqualTo(FLIGHT_TICKS + COURSE_CLEAR_TICKS);

        // Execute & Verify - the clock: a jingle 300 ticks long holds the bonus back past the timer
        musicTicks = 300;
        playerData.clearCards();
        levelCompletion.reset();
        startTheTimerAt(287);
        levelCompletion.collect(FLOWER, player);
        assertThat(ticksUntil(EXITING)).as("The clock: 35 steps from tick 300, done on tick 335").isEqualTo(335);

        // Execute & Verify - the player: not at the exit until it is moved there
        musicTicks = 0;
        playerData.clearCards();
        levelCompletion.reset();
        startTheTimerAt(287);
        position.setX(100);
        levelCompletion.collect(FLOWER, player);
        tick(400);
        assertThat(levelCompletion.getPhase()).as("The player is not at the exit").isEqualTo(COURSE_CLEAR);
        position.setX(EXIT_X);
        tick(1);
        assertThat(levelCompletion.getPhase()).isEqualTo(EXITING);
        assertThat(levelCompletion.getPhaseTimer()).isEqualTo(EXIT_TICKS);
    }

    @Test
    @DisplayName("LevelCompleted is published once, 128 ticks after the exit begins")
    void theLevelEndsAfterTheExitTimer() {
        // Prepare
        final List<GameEvent> published = recordPublishedEvents();
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        ticksUntil(EXITING);

        // Execute & Verify
        tick(EXIT_TICKS - 1);
        assertThat(published).as("Still exiting").noneMatch(LevelCompleted.class::isInstance);
        tick(1);
        assertThat(published).as("Ended").filteredOn(LevelCompleted.class::isInstance).hasSize(1);
        assertThat(levelCompletion.isCompleted()).isTrue();
        tick(200);
        assertThat(published).as("Once").filteredOn(LevelCompleted.class::isInstance).hasSize(1);
    }

    @Test
    @DisplayName("Three cards are cleared when the level ends; one or two stay")
    void threeCardsAreClearedAtTheEnd() {
        // Execute & Verify - the third card
        playerData.addCard(MUSHROOM);
        playerData.addCard(STAR);
        runToTheEnd();
        assertThat(playerData.getCards()).as("Three").containsExactly(null, null, null);

        // Execute & Verify - the first and the second
        levelCompletion.reset();
        runToTheEnd();
        assertThat(playerData.getCards()).as("One").containsExactly(FLOWER, null, null);
        levelCompletion.reset();
        runToTheEnd();
        assertThat(playerData.getCards()).as("Two").containsExactly(FLOWER, FLOWER, null);
    }

    @Test
    @DisplayName("The player is halted at sprite X 32 or 33, before or after the wrap, during the flight too")
    void thePlayerIsHaltedAtTheExit() {
        // Prepare
        startTheTimerAt(0);
        position.setX(31.9);
        levelCompletion.collect(FLOWER, player);

        // Execute & Verify
        tick(1);
        verify(player, never()).haltRunOff();
        for (final double x : new double[] {32, 33.9, 288, 289.5}) {
            position.setX(x);
            tick(1);
        }
        assertThat(levelCompletion.getPhase()).isEqualTo(CARD_FLYING_OFF);
        verify(player, times(4)).haltRunOff();
        position.setX(290);
        tick(1);
        verify(player, times(4)).haltRunOff();
    }

    @Test
    @DisplayName("Nothing happens before the card is touched, or once the player is dying")
    void nothingWithoutALivingRunOff() {
        // Prepare
        final List<GameEvent> published = recordPublishedEvents();
        startTheTimerAt(287);
        position.setX(EXIT_X);

        // Execute & Verify
        tick(10);
        assertThat(published).as("No card yet").isEmpty();
        levelCompletion.collect(FLOWER, player);
        when(runtimeState.isDying()).thenReturn(true);
        tick(1000);
        assertThat(levelCompletion.getPhase()).as("Dying").isEqualTo(CARD_FLYING_OFF);
        assertThat(playerData.getTimer()).isEqualTo(287);
        assertThat(published).noneMatch(LevelCompleted.class::isInstance);
    }

    @Test
    @DisplayName("On the touch, an enemy, a held shell and a mushroom in view leave a poof and a coin; one out of view "
        + "is removed with no coin")
    void theTouchClearsTheObjects() {
        // Prepare
        final EnemyLevelObject goomba = enemyAt(100, 120);
        final EnemyLevelObject shell = mock(EnemyLevelObject.class, withSettings().extraInterfaces(
            CarriableLevelObject.class
        ));
        placeAt(shell, 120, 104);
        when(shell.isSpawnedIntoScene()).thenReturn(true);
        when(runtimeState.getCarriedObject()).thenReturn((CarriableLevelObject) shell);
        final RewardLevelObject mushroom = mock(RewardLevelObject.class);
        placeAt(mushroom, 60, 140);
        final EnemyLevelObject outOfView = enemyAt(300, 120);
        final List<ActiveLevelObject> enemies = new ArrayList<>(List.of(goomba, shell, outOfView));
        final List<ActiveLevelObject> rewards = new ArrayList<>(List.of(mushroom));
        final SweepManagers managers = sweepManagers(enemies, rewards);

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        verify(managers.coins()).spawnPoppedOutCoin(player, goomba.getPosition());
        verify(managers.coins()).spawnPoppedOutCoin(player, shell.getPosition());
        verify(managers.coins()).spawnPoppedOutCoin(player, mushroom.getPosition());
        verify(managers.coins(), never()).spawnPoppedOutCoin(player, outOfView.getPosition());
        verify(managers.poofs(), times(3)).spawnPoofDeath(eq(gameEngine), any());
        for (final ActiveLevelObject object : List.of(goomba, shell, mushroom, outOfView)) {
            verify(object).expire();
            verify(object).detach();
        }
        assertThat(enemies).isEmpty();
        assertThat(rewards).isEmpty();
        verify(managers.grid()).retirePlacementOf(goomba);
        verify(managers.grid()).retirePlacementOf(shell);
        verify(managers.grid()).retirePlacementOf(outOfView);
        verify(runtimeState).setCarriedObject(null);
    }

    @Test
    @DisplayName("The poof is a 16 x 16 cloud over the object's top-left")
    void thePoofCoversTheObject() {
        // Prepare
        final EnemyLevelObject goomba = enemyAt(100, 120);
        final SweepManagers managers = sweepManagers(new ArrayList<>(List.of(goomba)), new ArrayList<>());

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify - its bottom-left corner, in world units with Y up: x 100 / 16, y rows - (120 + 16) / 16
        final ArgumentCaptor<WorldOffset> offset = ArgumentCaptor.forClass(WorldOffset.class);
        verify(managers.poofs()).spawnPoofDeath(eq(gameEngine), offset.capture());
        assertThat(offset.getValue().x()).isCloseTo(100f / 16, within(1e-6f));
        assertThat(offset.getValue().y()).isCloseTo(ROWS - 136f / 16, within(1e-6f));
    }

    @Test
    @DisplayName("Every special object goes: the piranha fireballs and the switch-block puffs")
    void theSpecialObjectsAreRemoved() {
        // Prepare
        final SweepManagers managers = sweepManagers(new ArrayList<>(), new ArrayList<>());

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        verify(managers.fireballs()).removeAll();
        verify(managers.poofs()).removeSwitchPuffs();
    }

    @Test
    @DisplayName("Enemies the scroll has not reached, a reward collected this tick and the card itself are left alone")
    void objectsInNoSlotAreLeftAlone() {
        // Prepare
        final EnemyLevelObject notYetReached = enemyAt(100, 120);
        when(notYetReached.isSpawnedIntoScene()).thenReturn(false);
        final RewardLevelObject collected = mock(RewardLevelObject.class);
        placeAt(collected, 60, 140);
        when(collected.isCollected()).thenReturn(true);
        final List<ActiveLevelObject> enemies = new ArrayList<>(List.of(notYetReached));
        final List<ActiveLevelObject> rewards = new ArrayList<>(List.of(collected));
        final SweepManagers managers = sweepManagers(enemies, rewards);

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        assertThat(enemies).containsExactly(notYetReached);
        assertThat(rewards).containsExactly(collected);
        assertThat(managers.cards()).hasSize(1);
        verify(managers.coins(), never()).spawnPoppedOutCoin(any(), any());
    }

    @Test
    @DisplayName("Only a 16 x 16 box at the object's X and Y is tested: X on the 256 px screen, Y + 16 in 240 lines")
    void theOnScreenTest() {
        // Prepare
        final AxisAlignedBoundingBox screen = new AxisAlignedBoundingBox(100, 50, 356, 242);

        // Execute & Verify
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(100, 50), screen)).as("Top-left").isTrue();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(99.5, 50), screen)).as("1 px off left").isFalse();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(355.9, 50), screen)).as("Mostly off right").isTrue();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(356, 50), screen)).as("Off right").isFalse();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(100, 34), screen)).as("Just above, bottom at top")
            .isTrue();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(100, 33), screen)).as("Off top").isFalse();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(100, 273), screen)).as("Bottom at line 239").isTrue();
        assertThat(LevelCompletion.isOnScreen(new PixelPosition(100, 274), screen)).as("Off bottom").isFalse();
    }

    @Test
    @DisplayName("COURSE CLEAR! shows 66 ticks into state 5, YOU GOT A CARD 95 ticks in, and both stay to the end")
    void theMessagesShowOnTheStateTimer() {
        // Prepare
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(STAR, player);
        ticksUntil(COURSE_CLEAR);
        final Message duringTheFlight = levelCompletion.getMessage();

        // Execute & Verify
        tick(COURSE_CLEAR_TICKS - COURSE_CLEAR_MESSAGE_TICK - 1);
        assertThat(levelCompletion.getMessage()).as("65 ticks in").isEqualTo(Message.NONE);
        tick(1);
        assertThat(levelCompletion.getMessage()).as("66 ticks in").isEqualTo(Message.COURSE_CLEAR);
        tick(COURSE_CLEAR_MESSAGE_TICK - YOU_GOT_A_CARD_TICK - 1);
        assertThat(levelCompletion.getMessage()).as("94 ticks in").isEqualTo(Message.COURSE_CLEAR);
        tick(1);
        assertThat(levelCompletion.getMessage()).as("95 ticks in").isEqualTo(Message.YOU_GOT_A_CARD);
        assertThat(levelCompletion.getCollectedCard()).as("The card in the box").isEqualTo(STAR);
        ticksUntil(COMPLETED);
        assertThat(levelCompletion.getMessage()).as("At the end").isEqualTo(Message.YOU_GOT_A_CARD);
        assertThat(duringTheFlight).as("During the flight").isEqualTo(Message.NONE);
        levelCompletion.reset();
        assertThat(levelCompletion.getMessage()).as("On the next run").isEqualTo(Message.NONE);
    }

    @Test
    @DisplayName("The status bar hides the new card until 96 ticks into state 5, holds it 47 ticks, then flashes it")
    void theNewCardReachesTheStatusBarAfterTheMessage() {
        // Prepare - the player short of the exit, so state 5 goes on after its timer
        startTheTimerAt(0);
        position.setX(100);
        final boolean beforeTheTouch = levelCompletion.isCardSlotHidden(0);
        levelCompletion.collect(FLOWER, player);
        final boolean onTheTouch = levelCompletion.isCardSlotHidden(0);

        // Execute & Verify - hidden through the flight and the first 95 ticks of state 5
        final int flight = ticksUntil(COURSE_CLEAR);
        assertThat(levelCompletion.isCardSlotHidden(0)).as("As state 5 starts, %d ticks in", flight).isTrue();
        for (int tick = 1; tick < COURSE_CLEAR_TICKS; tick++) {
            tick(1);
            assertThat(levelCompletion.isCardSlotHidden(0)).as("%d ticks into state 5", tick).isTrue();
        }
        for (int tick = COURSE_CLEAR_TICKS; tick < COURSE_CLEAR_TICKS + NEWEST_CARD_HOLD_TICKS - 1; tick++) {
            tick(1);
            assertThat(levelCompletion.isCardSlotHidden(0)).as("%d ticks into state 5: held", tick).isFalse();
        }
        final List<Boolean> flashes = new ArrayList<>();
        for (int tick = 0; tick < 4 * FLASH_BIT; tick++) {
            tick(1);
            assertThat(levelCompletion.isCardSlotHidden(0)).as("Flashing, frame %d", frameCounter.getTicks())
                .isEqualTo((frameCounter.getTicks() & FLASH_BIT) == 0);
            flashes.add(levelCompletion.isCardSlotHidden(0));
        }
        position.setX(EXIT_X);
        tick(1);
        assertThat(levelCompletion.getPhase()).isEqualTo(EXITING);
        for (int tick = 0; tick < 2 * FLASH_BIT; tick++) {
            tick(1);
            assertThat(levelCompletion.isCardSlotHidden(0)).as("Exiting, frame %d", frameCounter.getTicks())
                .isEqualTo((frameCounter.getTicks() & FLASH_BIT) == 0);
        }
        assertThat(beforeTheTouch).as("Before the touch").isFalse();
        assertThat(onTheTouch).as("On the touch").isTrue();
        assertThat(levelCompletion.isCardSlotHidden(1)).as("The empty slots are left alone").isFalse();
        assertThat(playerData.getCards()).as("In the player's data throughout").containsExactly(FLOWER, null, null);
        assertThat(flashes).as("Both ways while flashing").contains(true, false);
        ticksUntil(COMPLETED);
        assertThat(levelCompletion.isCardSlotHidden(0)).as("Once the level has ended").isFalse();
    }

    @Test
    @DisplayName("When state 5 ends on the tick its timer runs out, the new card first shows on the exit's first tick")
    void theNewCardShowsWithTheExitWhenStateFiveEndsAtOnce() {
        // Prepare
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(STAR, player);
        ticksUntil(EXITING);

        // Execute & Verify
        assertThat(levelCompletion.isCardSlotHidden(0)).as("On the tick state 5 ends").isTrue();
        tick(1);
        assertThat(levelCompletion.isCardSlotHidden(0)).as("The exit's first tick, held").isFalse();
    }

    @Test
    @DisplayName("Three matching cards start the fanfare: the player stands still, the tile animations go on")
    void threeMatchingCardsStartTheFanfare() {
        // Prepare
        playerData.addCard(STAR);
        playerData.addCard(STAR);

        // Execute
        levelCompletion.collect(STAR, player);

        // Verify
        assertThat(levelCompletion.isFanfare()).isTrue();
        assertThat(levelCompletion.getPhase()).isEqualTo(FANFARE_RISING);
        verify(player).standStill();
        verify(player, never()).startRunningOff();
        verify(tileAnimator, never()).freeze();
        verify(gameEngine).publish(new LevelCardCollected(STAR, true));
        assertThat(levelCompletion.isCardSlotHidden(2)).as("The third card is not on the status bar yet").isTrue();
        assertThat(levelCompletion.isCardSlotHidden(0)).as("The first two are").isFalse();
    }

    @Test
    @DisplayName("Two matching cards and a third that differs, or only two cards, are no fanfare")
    void anyOtherCardsAreNoFanfare() {
        // Execute & Verify
        playerData.addCard(STAR);
        playerData.addCard(STAR);
        levelCompletion.collect(FLOWER, player);
        assertThat(levelCompletion.isFanfare()).as("Mixed").isFalse();
        playerData.clearCards();
        levelCompletion.reset();
        playerData.addCard(STAR);
        levelCompletion.collect(STAR, player);
        assertThat(levelCompletion.isFanfare()).as("Two").isFalse();
        assertThat(levelCompletion.getPhase()).isEqualTo(CARD_FLYING_OFF);
    }

    @Test
    @DisplayName("In the fanfare the card rises until it is above line 272, then the view scrolls up 3 px a tick until "
        + "its top is above line 17")
    void theFanfareScrollsTheViewUpToTheSky() {
        // Prepare - the card at line 336 of a 27-row level whose view starts at its bottom, line 240
        final LevelSceneVerticalScroll verticalScroll = aTallLevel();
        startTheFanfare(MUSHROOM);
        final List<Integer> viewTops = new ArrayList<>();

        // Execute
        for (int tick = 1; tick <= SKY_TICKS; tick++) {
            tick(1);
            viewTops.add(verticalScroll.getViewTop());
        }

        // Verify - the card is at line 271 on tick 26, the first scroll; 75 scrolls take the top from 240 to 15
        assertThat(viewTops.get(24)).as("Tick 25").isEqualTo(240);
        assertThat(viewTops.get(25)).as("Tick 26").isEqualTo(237);
        assertThat(viewTops.get(SKY_TICKS - 2)).as("Tick 99").isEqualTo(18);
        assertThat(viewTops.getLast()).as("Tick 100").isEqualTo(15);
        assertThat(levelCompletion.getPhaseTimer()).as("The twinkle starts").isEqualTo(TWINKLE_TICKS);
        tick(50);
        assertThat(verticalScroll.getViewTop()).as("No further").isEqualTo(15);
    }

    @Test
    @DisplayName("Once the view has reached the sky, the card twinkles for 15 ticks, then is gone as the fireworks "
        + "start fading in")
    void theCardTwinklesThenGoes() {
        // Prepare
        aTallLevel();
        startTheFanfare(MUSHROOM);
        tick(SKY_TICKS);
        final List<FanfareCard> twinkles = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 15; tick++) {
            assertThat(levelCompletion.getPhase()).as("Twinkle %d", tick).isEqualTo(FANFARE_RISING);
            tick(1);
            twinkles.add(levelCompletion.getFanfareCard());
        }

        // Verify
        assertThat(twinkles).containsExactly(
            LARGE_TWINKLE, LARGE_TWINKLE, LARGE_TWINKLE, LARGE_TWINKLE, LARGE_TWINKLE, LARGE_TWINKLE,
            SMALL_TWINKLE, SMALL_TWINKLE, LARGE_TWINKLE, LARGE_TWINKLE,
            SMALL_TWINKLE, SMALL_TWINKLE, SMALL_TWINKLE, SMALL_TWINKLE, SMALL_TWINKLE
        );
        assertThat(levelCompletion.getPhase()).isEqualTo(FANFARE_APPEARING);
        tick(1);
        assertThat(levelCompletion.getFanfareCard()).isEqualTo(FanfareCard.GONE);
    }

    @Test
    @DisplayName("The fireworks fade in a step every 4 frames, the first step changing nothing, then YOU GOT A "
        + "CARD is written in the sky 48 ticks into the message")
    void theFireworksFadeInThenTheMessage() {
        // Prepare - the twinkle ends on tick 115; ticks 116, 120 ... 136 are the frames that are multiples of 4
        aTallLevel();
        startTheFanfare(FLOWER);
        tick(SKY_TICKS + 15);
        final List<FireworksFade> fades = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 21; tick++) {
            tick(1);
            final FireworksPicture fireworks = levelCompletion.getScreen().fireworks();
            fades.add(fireworks == null ? null : fireworks.fade());
        }

        // Verify - from the middle out, whatever order the steps are declared in, each held for 4 frames
        final FireworksFade[] steps = {
            FireworksFade.MIDDLE,
            FireworksFade.MIDDLE_BRIGHT,
            FireworksFade.INNER_BRIGHT,
            FireworksFade.ALMOST_ALL_BRIGHT,
            FireworksFade.ALL_BRIGHT
        };
        assertThat(fades.subList(0, 4)).as("The first step").containsOnlyNulls();
        for (int step = 0; step < steps.length - 1; step++) {
            assertThat(fades.subList(4 + 4 * step, 8 + 4 * step)).as("Step %d", step + 1).containsOnly(steps[step]);
        }
        assertThat(fades.getLast()).as("The last step, all lit").isEqualTo(FireworksFade.ALL_BRIGHT);
        assertThat(levelCompletion.getPhase()).isEqualTo(FANFARE_MESSAGE);
        assertThat(levelCompletion.getScreen().fireworks().shape()).isEqualTo(FLOWER);
        assertThat(levelCompletion.getScreen().viewTop()).as("Drawn from the view's top").isEqualTo(15);
        tick(FANFARE_MESSAGE_TICKS - FANFARE_MESSAGE_TICK - 1);
        assertThat(levelCompletion.getMessage()).isEqualTo(Message.NONE);
        assertThat(levelCompletion.isCardSlotHidden(2)).isTrue();
        tick(1);
        assertThat(levelCompletion.getMessage()).isEqualTo(Message.FANFARE_YOU_GOT_A_CARD);
        tick(1);
        assertThat(levelCompletion.isCardSlotHidden(2)).as("On the status bar from the next tick").isFalse();
    }

    @Test
    @DisplayName("The fanfare's time bonus waits for the victory fanfare's end; the course clear's end does not count")
    void theFanfaresBonusWaitsForTheVictoryFanfare() {
        // Prepare - a fanfare that ends on the 400th tick, well after the message's timer
        aTallLevel();
        musicTicks = 400;
        playerData.addCard(FLOWER);
        playerData.addCard(FLOWER);
        startTheTimerAt(287);
        levelCompletion.collect(FLOWER, player);
        ticksUntil(FANFARE_MESSAGE);

        // Execute & Verify
        levelCompletion.onSoundPlayComplete(new SoundPlayComplete(MusicTrack.COURSE_CLEAR));
        tick(FANFARE_MESSAGE_TICKS + 10);
        assertThat(playerData.getTimer()).as("Still playing").isEqualTo(287);
        assertThat(levelCompletion.getPhase()).isEqualTo(FANFARE_MESSAGE);
        ticksUntil(PAYING_OUT_LIVES);
        assertThat(playerData.getTimer()).as("Converted once it has ended").isZero();
    }

    @Test
    @DisplayName("The sparkle of the fireworks' dots changes every 8 frames, through 4 pictures")
    void theFireworksSparkle() {
        // Prepare
        aTallLevel();
        startTheFanfare(STAR);
        ticksUntil(FANFARE_MESSAGE);
        final List<Integer> sparkles = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 40; tick++) {
            tick(1);
            sparkles.add(levelCompletion.getScreen().fireworks().sparkle());
            assertThat(sparkles.getLast()).isEqualTo(frameCounter.getTicks() / 8 % 4);
        }

        // Verify
        assertThat(sparkles).containsOnly(0, 1, 2, 3);
    }

    @Test
    @DisplayName("Three Mushrooms give 2 lives, three Flowers 3 and three Stars 5, each with the 1-up sound, and the "
        + "cards are cleared")
    void threeMatchingCardsPayOutLives() {
        // Execute & Verify
        for (final LevelCompletionCardType card : LevelCompletionCardType.values()) {
            final int lives = switch (card) {
                case MUSHROOM -> 2;
                case FLOWER -> 3;
                case STAR -> 5;
            };
            aTallLevel();
            final int before = playerData.getLives();
            final SoundPlayer sounds = mock(SoundPlayer.class);
            staticBeanFactory.when(() -> StaticBeanFactory.getBean(SoundPlayer.class)).thenReturn(sounds);
            startTheFanfare(card);
            ticksUntil(COMPLETED);
            assertThat(playerData.getLives() - before).as("%s", card).isEqualTo(lives);
            verify(sounds, times(lives)).play(ONE_UP);
            verify(sounds).muteEffectsExcept(CASINO_BEEP, ONE_UP);
            assertThat(playerData.getCards()).as("%s", card).containsExactly(null, null, null);
            levelCompletion.reset();
        }
    }

    @Test
    @DisplayName("Three mixed cards give 1 life once state 5 ends, then the exit follows")
    void threeMixedCardsPayOutOneLife() {
        // Prepare
        playerData.addCard(MUSHROOM);
        playerData.addCard(STAR);
        startTheTimerAt(0);
        position.setX(EXIT_X);
        final int before = playerData.getLives();
        levelCompletion.collect(FLOWER, player);

        // Execute
        final int toThePayout = ticksUntil(PAYING_OUT_LIVES);
        final int payout = ticksUntil(EXITING);

        // Verify - one life on the payout's first tick; it rises 32 ticks and bounces 63, and exits on the 96th
        assertThat(toThePayout).isEqualTo(FLIGHT_TICKS + COURSE_CLEAR_TICKS);
        assertThat(payout).isEqualTo(96);
        assertThat(playerData.getLives() - before).isEqualTo(1);
        verify(soundPlayer).play(ONE_UP);
        assertThat(levelCompletion.getScreen().livesBonus())
            .isEqualTo(new LivesBonusPicture(1, LIVES_BONUS_X, 127));
        ticksUntil(COMPLETED);
        assertThat(playerData.getCards()).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("The nUP starts below the view, rises 4 px a tick while its line is 128 or more, then bounces 1 px")
    void theLivesBonusRisesThenBounces() {
        // Prepare
        playerData.addCard(MUSHROOM);
        playerData.addCard(STAR);
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        ticksUntil(PAYING_OUT_LIVES);
        final List<Integer> ys = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 36; tick++) {
            tick(1);
            ys.add(levelCompletion.getScreen().livesBonus().y());
        }

        // Verify - drawn one line below its line as each tick began
        assertThat(ys.getFirst()).isEqualTo(256);
        assertThat(ys.get(1)).isEqualTo(252);
        assertThat(ys.get(31)).as("Tick 32").isEqualTo(132);
        assertThat(ys.subList(32, 36)).as("Bouncing").containsExactly(128, 127, 128, 127);
        assertThat(levelCompletion.getScreen().livesBonus().x()).isEqualTo(LIVES_BONUS_X);
    }

    @Test
    @DisplayName("Five lives come one 1-up sound apart, 48 ticks, and the exit starts with the last of them")
    void livesComeOneSoundApart() {
        // Prepare
        aTallLevel();
        final int before = playerData.getLives();
        startTheFanfare(STAR);
        ticksUntil(PAYING_OUT_LIVES);
        final List<Integer> given = new ArrayList<>();

        // Execute
        for (int tick = 1; levelCompletion.getPhase() == PAYING_OUT_LIVES; tick++) {
            final int lives = playerData.getLives();
            tick(1);
            if (playerData.getLives() > lives) {
                given.add(tick);
            }
        }

        // Verify
        assertThat(given).containsExactly(1, 49, 97, 145, 193);
        assertThat(playerData.getLives() - before).isEqualTo(5);
        assertThat(levelCompletion.getScreen().livesBonus().x()).as("Without the 8 px of the ground")
            .isEqualTo(FANFARE_LIVES_BONUS_X);
        assertThat(levelCompletion.getScreen().livesBonus().lives()).isEqualTo(5);
    }

    @Test
    @DisplayName("While lives are paid out the cards blink off in turn: one empty for 7 frames, drawn on the 8th")
    void theCardsBlinkOffInTurn() {
        // Prepare - the payout starts after tick 162, so its first tick is frame 163
        playerData.addCard(MUSHROOM);
        playerData.addCard(STAR);
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        ticksUntil(PAYING_OUT_LIVES);
        final List<String> hidden = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 24; tick++) {
            tick(1);
            hidden.add(hiddenSlots());
        }

        // Verify - the third card, never drawn since the touch, shows once its turn has come
        assertThat(hidden.subList(0, 5)).as("Frames 163-167").containsOnly("0 2");
        assertThat(hidden.get(5)).as("Frame 168").isEqualTo("2");
        assertThat(hidden.subList(6, 13)).as("Frames 169-175").containsOnly("1 2");
        assertThat(hidden.get(13)).as("Frame 176").isEqualTo("2");
        assertThat(hidden.subList(14, 21)).as("Frames 177-183").containsOnly("2");
        assertThat(hidden.get(21)).as("Frame 184").isEmpty();
        assertThat(hidden.get(22)).as("Frame 185").isEqualTo("0");
    }

    @Test
    @DisplayName("The touch mutes every sound effect but the time bonus's beep, before its sweep pops out any coin")
    void theTouchMutesTheEffects() {
        // Prepare
        final EnemyLevelObject goomba = enemyAt(100, 120);
        final SweepManagers managers = sweepManagers(new ArrayList<>(List.of(goomba)), new ArrayList<>());

        // Execute
        levelCompletion.collect(FLOWER, player);

        // Verify
        final InOrder order = inOrder(soundPlayer, managers.coins());
        order.verify(soundPlayer).muteEffectsExcept(CASINO_BEEP);
        order.verify(managers.coins()).spawnPoppedOutCoin(player, goomba.getPosition());
    }

    private record SweepManagers(
        CoinRewardMotionManager coins,
        PoofMotionManager poofs,
        PiranhaFireballMotionManager fireballs,
        ActiveObjectGrid<ActiveLevelObject> grid,
        List<ActiveLevelObject> cards
    ) {
    }

    @SuppressWarnings("unchecked")
    private SweepManagers sweepManagers(
        final List<ActiveLevelObject> enemies,
        final List<ActiveLevelObject> rewards
    ) {
        final ActiveLevelObjectMotionManager<ActiveLevelObject> enemyManager =
            mock(ActiveLevelObjectMotionManager.class);
        doReturn(enemies).when(enemyManager).getActiveInstances();
        final ActiveLevelObjectMotionManager<ActiveLevelObject> rewardManager =
            mock(ActiveLevelObjectMotionManager.class);
        doReturn(rewards).when(rewardManager).getActiveInstances();
        final LevelCompletionCardMotionManager cardManager = mock(LevelCompletionCardMotionManager.class);
        final List<ActiveLevelObject> cards = new ArrayList<>(List.of(mock(ActiveLevelObject.class)));
        doReturn(cards).when(cardManager).getActiveInstances();
        final CoinRewardMotionManager coins = mock(CoinRewardMotionManager.class);
        final PoofMotionManager poofs = mock(PoofMotionManager.class);
        final PiranhaFireballMotionManager fireballs = mock(PiranhaFireballMotionManager.class);
        doReturn(List.of(enemyManager, rewardManager, cardManager, coins, poofs, fireballs))
            .when(gameEngine).getMotionManagers();
        final ActiveObjectGrid<ActiveLevelObject> grid = mock(ActiveObjectGrid.class);
        when(gameEngine.getActiveObjectGrid()).thenReturn(grid);
        return new SweepManagers(coins, poofs, fireballs, grid, cards);
    }

    private static EnemyLevelObject enemyAt(final double x, final double y) {
        final EnemyLevelObject enemy = mock(EnemyLevelObject.class);
        placeAt(enemy, x, y);
        when(enemy.isSpawnedIntoScene()).thenReturn(true);
        return enemy;
    }

    private static void placeAt(final ActiveLevelObject object, final double x, final double y) {
        when(object.getPosition()).thenReturn(new PixelPosition(x, y));
    }

    private void startTheTimerAt(final int time) {
        // Not clear(): that publishes through the bean factory, which this test mocks
        final PlayerTimer timer = playerData.getPlayerTimer();
        timer.setTime(0);
        timer.setInitialTime(time);
        timer.start();
    }

    private void runToTheEnd() {
        startTheTimerAt(0);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        ticksUntil(COMPLETED);
    }

    /**
     * Makes the level 27 rows tall, with the card at row 21 (line 336) and the view at the level's bottom, its top at
     * line 240, and starts a new run in it.
     *
     * @return the player's vertical scroll, which the fanfare drives
     */
    private LevelSceneVerticalScroll aTallLevel() {
        when(levelScene.getLevelCompletionCard()).thenReturn(of(30, 21));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(64, TALL_ROWS));
        final LevelSceneVerticalScroll verticalScroll = new LevelSceneVerticalScroll(levelScene);
        when(player.getVerticalScroll()).thenReturn(verticalScroll);
        levelCompletion.reset();
        return verticalScroll;
    }

    /** Touches the third card of a kind, with no time left to convert. */
    private void startTheFanfare(final LevelCompletionCardType card) {
        startTheTimerAt(0);
        playerData.addCard(card);
        playerData.addCard(card);
        levelCompletion.collect(card, player);
    }

    /** @return the slots the status bar shows empty, in order, separated by a space */
    private String hiddenSlots() {
        final List<String> hidden = new ArrayList<>();
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            if (levelCompletion.isCardSlotHidden(slot)) {
                hidden.add(String.valueOf(slot));
            }
        }
        return String.join(" ", hidden);
    }

    private int ticksUntil(final LevelCompletion.Phase phase) {
        int ticks = 0;
        while (levelCompletion.getPhase() != phase) {
            assertThat(ticks).as("Reaching %s", phase).isLessThan(10_000);
            tick(1);
            ticks++;
        }
        return ticks;
    }

    /**
     * Ticks the completion. Before each tick the touch's jingle (the victory fanfare for three matching cards, the
     * course clear otherwise) plays on for {@link #musicTicks} more ticks, counted from the touch; once they are spent
     * its end is heard, so it ends on that tick, as a jingle that long would.
     */
    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            if (levelCompletion.isUnderway() && musicTicks > 0) {
                musicTicks--;
            }
            if (musicTicks == 0) {
                levelCompletion.onSoundPlayComplete(new SoundPlayComplete(
                    levelCompletion.isFanfare() ? MusicTrack.VICTORY_FANFARE : MusicTrack.COURSE_CLEAR
                ));
            }
            frameCounter.tick();
            levelCompletion.tick();
        }
    }

    private List<GameEvent> recordPublishedEvents() {
        final List<GameEvent> published = new ArrayList<>();
        doAnswer(invocation -> published.add(invocation.getArgument(0))).when(gameEngine).publish(any());
        return published;
    }
}
