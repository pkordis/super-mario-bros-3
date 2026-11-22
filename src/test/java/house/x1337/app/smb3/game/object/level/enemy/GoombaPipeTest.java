package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.motion.GoombaPipeMotionManager;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Goomba pipe's rhythm and rules. The dispenser sits on cell (5, 5), right of a side pipe's lip on (4, 5), so its
 * Goombas start on the lip, on pixel (64, 77), and slide out to the right.
 */
class GoombaPipeTest {
    private static final int COLUMNS = 10;
    private static final int ROWS = 8;
    private static final int SPAWN_X = 64;

    private final PlayerPosition playerPosition = new PlayerPosition();
    private final List<Integer> dispensedOnTicks = new ArrayList<>();
    private final List<Goomba> dispensed = new ArrayList<>();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelCompletion levelCompletion;
    private ActiveObjectGrid<ActiveLevelObject> activeObjectGrid;
    private GoombaPipe goombaPipe;
    private int tick;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(playerPosition);
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));

        levelCompletion = mock(LevelCompletion.class);
        activeObjectGrid = mock(ActiveObjectGrid.class);
        doAnswer(invocation -> {
            dispensedOnTicks.add(tick);
            return null;
        }).when(activeObjectGrid).spawnEnemy(any());

        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getActiveObjectGrid()).thenReturn(activeObjectGrid);
        when(gameEngine.getLevelCompletion()).thenReturn(levelCompletion);

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(Goomba.class), any(), any()))
            .thenAnswer(invocation -> {
                final Goomba goomba = mock(Goomba.class);
                dispensed.add(goomba);
                return goomba;
            });

        goombaPipe = new GoombaPipe(gameEngine, of(5, 5));
        goombaPipe.init();
        playerPosition.setX(200);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Goombas come out 96, 208 and 320 ticks in, then every 112 ticks with every fourth pulse skipped")
    void dispensesThreePulsesInFour() {
        // Execute
        runTo(1000);

        // Verify
        assertThat(dispensedOnTicks).containsExactly(96, 208, 320, 544, 656, 768, 992);
    }

    @Test
    @DisplayName("A Goomba slides out to the right, from 3 px above the cell left of the dispenser")
    void dispensesRightFromTheCellOnTheLeft() {
        // Execute
        runTo(96);

        // Verify
        assertThat(dispensed).hasSize(1);
        verify(dispensed.getFirst()).slideOutOfAPipe(new PixelPosition(SPAWN_X, 77), true);
        verify(activeObjectGrid).spawnEnemy(dispensed.getFirst());
        staticBeanFactory.verify(() -> StaticBeanFactory.getBean(eq(Goomba.class), any(), eq(of(4, 5))));
    }

    @Test
    @DisplayName("No Goomba comes out while the player is left of its start, and the pulse is still spent")
    void playerOnTheOtherSideSpendsThePulse() {
        // Prepare
        playerPosition.setX(SPAWN_X - 1);
        runTo(150);
        playerPosition.setX(SPAWN_X);

        // Execute
        runTo(320);

        // Verify
        assertThat(dispensedOnTicks).containsExactly(208, 320);
    }


    @Test
    @DisplayName("Once the level completion card is taken, the pipe stops")
    void stopsOnceTheCardIsTaken() {
        // Prepare
        when(levelCompletion.isUnderway()).thenReturn(true);

        // Execute
        runTo(400);

        // Verify
        assertThat(dispensedOnTicks).isEmpty();
    }

    @Test
    @DisplayName("The pipe is never deleted off screen, never hit, and not swept as an object")
    void isAPermanentInvisibleDispenser() {
        // Prepare
        final GoombaPipeMotionManager manager = new GoombaPipeMotionManager();

        // Execute & Verify
        assertThat(manager.isExemptFromDeletionOffScreen(goombaPipe)).isTrue();
        assertThat(goombaPipe.isHittable()).isFalse();
        assertThat(goombaPipe.canHurtPlayer()).isFalse();
        assertThat(goombaPipe.isSlotless()).isTrue();
    }

    @Test
    @DisplayName("While gameplay is halted the pipe's clock does not run")
    void haltedGameplayFreezesThePipe() {
        // Prepare
        final GoombaPipe halted = mock(GoombaPipe.class);
        final GoombaPipeMotionManager manager = new GoombaPipeMotionManager();
        manager.spawn(halted);

        // Execute
        manager.updateWhileHalted();

        // Verify
        verify(halted, never()).motionUpdate();
    }

    private void runTo(final int lastTick) {
        while (tick < lastTick) {
            tick++;
            goombaPipe.motionUpdate();
        }
    }

}
