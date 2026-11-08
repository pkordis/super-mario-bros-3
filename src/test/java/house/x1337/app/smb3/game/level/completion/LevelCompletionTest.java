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
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Touching the card starts the level's completion, as {@code ObjHit_EndLevelCard} does (dasm prg002.asm:6146-6292):
 * {@code Player_GetCard}, {@code Level_TimerEn = $81}, and {@code Level_PSwitchCnt}, {@code Kill_Tally} and
 * {@code Player_YVel} cleared.
 */
class LevelCompletionTest {
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
}
