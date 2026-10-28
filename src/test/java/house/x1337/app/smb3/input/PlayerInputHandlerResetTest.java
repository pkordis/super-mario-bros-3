package house.x1337.app.smb3.input;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.KeyInputEvent;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RESET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins R the way the layer toggle works: the handler publishes a {@link LevelSceneResetRequested} carrying
 * its own id on the press, and nothing on the release or while R is held.
 */
class PlayerInputHandlerResetTest {
    private final List<LevelSceneResetRequested> published = new ArrayList<>();

    private GameEngine gameEngine;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final GameEventBus gameEventBus = new GameEventBus();
        gameEventBus.subscribe(LevelSceneResetRequested.class, published::add);
        gameEngine = mock(GameEngine.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GameEventBus.class))
            .thenReturn(gameEventBus);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Pressing R publishes one reset request naming the handler that raised it")
    void pressingPublishes() {
        // Prepare
        final PlayerInputHandler handler = new PlayerInputHandler(gameEngine);

        // Execute
        handler.onAction(HANDLER_RESET, true, 0f);

        // Verify
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().getInputHandlerId()).isEqualTo(handler.getId());
    }

    @Test
    @DisplayName("Releasing R publishes nothing")
    void releasingPublishesNothing() {
        // Prepare
        final PlayerInputHandler handler = new PlayerInputHandler(gameEngine);

        // Execute
        handler.onAction(HANDLER_RESET, false, 0f);

        // Verify
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("Any other key publishes no reset request")
    void otherKeysPublishNothing() {
        // Prepare
        final PlayerInputHandler handler = new PlayerInputHandler(gameEngine);

        // Execute
        handler.onAction(HANDLER_JUMP, true, 0f);

        // Verify
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("Holding R through the OS auto-repeat still produces exactly one request")
    void holdingRProducesOneRequest() {
        // Prepare - a real jME input manager, fed by a keyboard that raises queued events on update
        final Deque<KeyInputEvent> keyboard = new ArrayDeque<>();
        final KeyInput keys = mock(KeyInput.class);
        final ArgumentCaptor<RawInputListener> rawListener = ArgumentCaptor.forClass(RawInputListener.class);
        final InputManager inputManager = new InputManager(mock(MouseInput.class), keys, null, null);
        verify(keys).setInputListener(rawListener.capture());
        doAnswer(invocation -> {
            while (!keyboard.isEmpty()) {
                rawListener.getValue().onKeyEvent(keyboard.poll());
            }
            return null;
        }).when(keys).update();
        when(gameEngine.getInputManager()).thenReturn(inputManager);
        new PlayerInputHandler(gameEngine).init();

        // Execute - press, three auto-repeats, release, each on its own frame
        keyboard.add(new KeyInputEvent(KeyInput.KEY_R, 'r', true, false));
        inputManager.update(1 / 60f);
        for (int repeat = 0; repeat < 3; repeat++) {
            keyboard.add(new KeyInputEvent(KeyInput.KEY_R, 'r', true, true));
            inputManager.update(1 / 60f);
        }
        keyboard.add(new KeyInputEvent(KeyInput.KEY_R, 'r', false, false));
        inputManager.update(1 / 60f);

        // Verify
        assertThat(published)
            .as("jME drops repeat events before they reach a mapping, and the release is ignored")
            .hasSize(1);
    }
}
