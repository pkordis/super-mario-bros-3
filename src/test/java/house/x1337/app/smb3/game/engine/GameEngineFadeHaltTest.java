package house.x1337.app.smb3.game.engine;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.jme3.core.CameraState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static house.x1337.app.smb3.game.engine.fade.FadeProfile.STANDARD;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.TOAD_AND_KING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A blocking fade halts the engine's gameplay as a halting player does: nothing else runs while the screen
 * fades in or out. Built on an engine that is never started.
 */
class GameEngineFadeHaltTest {
    private final FadingEngine fadingEngine = new FadingEngine();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final MotionManager.Registry registry = mock(MotionManager.Registry.class);
        doReturn(List.of()).when(registry).getAll();
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(MotionManager.Registry.class)).thenReturn(registry);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(FadingEngine.class)).thenReturn(fadingEngine);
        gameEngine = new GameEngine(mock(CameraState.class), mock(PlayerData.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A blocking fade halts gameplay while it runs and while the screen is held black")
    void blockingFadeHaltsGameplay() {
        // Execute & Verify
        assertThat(gameEngine.isGameplayHalted()).as("No fade").isFalse();

        fadingEngine.fadeOut(STANDARD, null);
        assertThat(gameEngine.isGameplayHalted()).as("Fading out").isTrue();

        for (int tick = 0; tick < 20; tick++) {
            fadingEngine.tick();
        }
        assertThat(gameEngine.isGameplayHalted()).as("Held black").isTrue();

        fadingEngine.fadeIn(STANDARD, null);
        for (int tick = 0; tick < 16; tick++) {
            fadingEngine.tick();
        }
        assertThat(gameEngine.isGameplayHalted()).as("Faded back in").isFalse();
    }

    @Test
    @DisplayName("A fade that does not block leaves gameplay running")
    void nonBlockingFadeDoesNotHalt() {
        // Execute
        fadingEngine.fadeOut(TOAD_AND_KING, null);

        // Verify
        assertThat(gameEngine.isGameplayHalted()).isFalse();
    }

    @Test
    @DisplayName("The engine exposes the fading engine it ticks")
    void exposesTheFadingEngine() {
        // Execute & Verify
        assertThat(gameEngine.getFadingEngine()).isSameAs(fadingEngine);
    }
}
