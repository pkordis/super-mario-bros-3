package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A pause that starts while the camera is scrolling, mid-flight, holds the camera still: the render frames between
 * the paused ticks find nothing to ease across. Before, they kept easing across the last scrolling tick's move, so the
 * view flickered for the whole pause wherever the screen refreshes faster than the simulation.
 */
class PlayerPauseCameraTest {
    private static final float TOLERANCE = 1e-6f;
    private static final int ROWS = 20;

    private GameEngine gameEngine;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(16, ROWS));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(mock(CameraState.class));
        when(gameEngine.getFadingEngine()).thenReturn(new FadingEngine());
        when(gameEngine.getRootNode()).thenReturn(new Node("Root"));
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.getPowerSwitchTimeWindow()).thenReturn(new PowerSwitchTimeWindow());
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == LevelSceneVerticalScroll.class) {
                return new LevelSceneVerticalScroll(levelScene);
            }
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Losing the raccoon suit mid-flight holds the camera still through the poof")
    void aSuitLossHoldsTheCamera() {
        assertTheCameraHoldsThrough(RACCOON, player -> player.getHurtBy(null));
    }

    @Test
    @DisplayName("Turning raccoon on a leaf mid-air holds the camera still through the poof")
    void turningRaccoonHoldsTheCamera() {
        assertTheCameraHoldsThrough(NORMAL, LevelScenePlayer::turnToRaccoon);
    }

    @Test
    @DisplayName("A death while the camera scrolls holds the camera still through the death")
    void aDeathHoldsTheCamera() {
        assertTheCameraHoldsThrough(SHRUNK, LevelScenePlayer::die);
    }

    private void assertTheCameraHoldsThrough(final PlayerMode mode, final Consumer<LevelScenePlayer> pause) {
        // Prepare - the camera moved up on the last scrolling tick
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        final LevelSceneVerticalScroll scroll = player.getVerticalScroll();
        scroll.update(15f, true);
        final float cameraY = scroll.update(15f, true);
        pause.accept(player);
        assertThat(player.isHaltingGameplay()).as("The pause halts gameplay").isTrue();

        // Execute & Verify - a few paused ticks, each followed by the render frames between it and the next
        for (int tick = 1; tick <= 5; tick++) {
            player.updateFrame();
            for (final double alpha : new double[] {0, 0.5, 1}) {
                assertThat(scroll.interpolate(alpha))
                    .as("Tick %d, alpha %s", tick, alpha)
                    .isCloseTo(cameraY, within(TOLERANCE));
            }
        }
    }
}
