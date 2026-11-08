package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCardSpawner;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.PlayerTimer;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEvent;
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.event.LevelCompleted;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.COURSE_CLEAR_TICKS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.EXIT_TICKS;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.CARD_FLYING_OFF;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.COMPLETED;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.COURSE_CLEAR;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.Phase.EXITING;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Touching the card starts the level's completion, as {@code ObjHit_EndLevelCard} does (dasm prg002.asm:6146-6292):
 * {@code Player_GetCard}, {@code Level_TimerEn = $81}, and {@code Level_PSwitchCnt}, {@code Kill_Tally} and
 * {@code Player_YVel} cleared. Then the card flies off, the clock turns into score and the level ends on the ROM's
 * timers ({@code EndLevelCard_WaitFlyAway}, {@code EndLevelCard_MsgAndCard}, {@code EndLevelCard_Exit}).
 */
class LevelCompletionTest {
    /** The card at row 9 (y 144) reaches y -19 after 65 ticks, so the screen top at 0 sees it gone on tick 66. */
    private static final int FLIGHT_TICKS = 66;

    /** Sprite X 32 once wrapped round, on a screen whose left edge is at 0. */
    private static final double EXIT_X = 288;
    private final PlayerData playerData = new PlayerData();
    private final PlayerPosition position = new PlayerPosition();
    private final PlayerRuntimeState runtimeState = mock(PlayerRuntimeState.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final GameObjectAnimator<?> tileAnimator = mock(GameObjectAnimator.class);
    private final MotionManager otherManager = mock(MotionManager.class);
    private final LevelScenePlayer player = mock(LevelScenePlayer.class);
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private LevelCompletion levelCompletion;

    @BeforeEach
    void prepare() {
        // LevelCompletion takes its card spawner from the bean factory; a real one reads the scene's card.
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
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
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt())).thenReturn(new AxisAlignedBoundingBox(0, 0, 256, 192));
        when(gameEngine.getCameraState()).thenReturn(cameraState);

        when(player.getPlayerData()).thenReturn(playerData);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);

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
        verify(gameEngine).publish(new LevelCardCollected(FLOWER));
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
    @DisplayName("Once the card is off screen the clock turns into score, one ROM step a tick, while the HUD shows it")
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
    @DisplayName("Nothing is converted while the course-clear music stand-in plays")
    void theBonusWaitsForTheMusic() {
        // Prepare - a 200-tick track
        levelCompletion = new LevelCompletion(gameEngine, new CourseClearMusicStandIn(200));
        levelCompletion.reset();
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);

        // Execute & Verify
        tick(199);
        assertThat(levelCompletion.getPhase()).isEqualTo(COURSE_CLEAR);
        assertThat(playerData.getTimer()).as("Still playing").isEqualTo(287);
        assertThat(playerData.getScore()).isZero();
        tick(1);
        assertThat(playerData.getTimer()).as("The track has ended").isEqualTo(277);
    }

    @Test
    @DisplayName("The exit waits for the course-clear timer, then the clock, then the player, in turn")
    void theExitWaitsForEachCondition() {
        // Execute & Verify - the timer: the clock is done long before its $60 ticks
        startTheTimerAt(287);
        position.setX(EXIT_X);
        levelCompletion.collect(FLOWER, player);
        assertThat(ticksUntil(EXITING)).as("The timer").isEqualTo(FLIGHT_TICKS + COURSE_CLEAR_TICKS);

        // Execute & Verify - the clock: a 300-tick track holds the bonus back past the timer
        levelCompletion = new LevelCompletion(gameEngine, new CourseClearMusicStandIn(300));
        levelCompletion.reset();
        startTheTimerAt(287);
        levelCompletion.collect(FLOWER, player);
        assertThat(ticksUntil(EXITING)).as("The clock: 35 steps from tick 300, done on tick 335").isEqualTo(335);

        // Execute & Verify - the player: not at the exit until it is moved there
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
    @DisplayName("LevelCompleted is published once, $80 ticks after the exit begins")
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

    private int ticksUntil(final LevelCompletion.Phase phase) {
        int ticks = 0;
        while (levelCompletion.getPhase() != phase) {
            assertThat(ticks).as("Reaching %s", phase).isLessThan(10_000);
            levelCompletion.tick();
            ticks++;
        }
        return ticks;
    }

    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            levelCompletion.tick();
        }
    }

    private List<GameEvent> recordPublishedEvents() {
        final List<GameEvent> published = new ArrayList<>();
        doAnswer(invocation -> published.add(invocation.getArgument(0))).when(gameEngine).publish(any());
        return published;
    }
}
