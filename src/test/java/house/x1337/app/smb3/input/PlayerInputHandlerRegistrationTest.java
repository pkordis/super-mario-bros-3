package house.x1337.app.smb3.input;

import com.jme3.input.InputManager;
import com.jme3.input.controls.Trigger;
import house.x1337.app.smb3.game.engine.GameEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LAYER_TOGGLE;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_SIZE_TOGGLE;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_UP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that player key mappings are registered once per engine, not once per handler. A fresh handler
 * is built every time a player is (re)spawned — notably on the death restart — but the mappings live on
 * the engine's single, long-lived {@code InputManager}, so a second handler must not re-add them (jME3
 * logs "Attempted to add mapping ... twice" when it does). The listener, by contrast, is per-handler
 * and is attached every time so the current player receives input.
 */
class PlayerInputHandlerRegistrationTest {
    private static final String[] MAPPINGS = {
        HANDLER_UP, HANDLER_DOWN, HANDLER_LEFT, HANDLER_RIGHT,
        HANDLER_JUMP, HANDLER_RUN, HANDLER_SIZE_TOGGLE, HANDLER_LAYER_TOGGLE
    };

    private InputManager inputManager;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        inputManager = mock(InputManager.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getInputManager()).thenReturn(inputManager);
    }

    @Test
    @DisplayName("The first handler installs every mapping once")
    void firstHandlerInstallsEveryMapping() {
        // Prepare - a virgin InputManager carries none of the mappings yet
        when(inputManager.hasMapping(any())).thenReturn(false);

        // Execute
        new PlayerInputHandler(gameEngine).init();

        // Verify - each mapping added exactly once, and the handler subscribed as the listener
        for (final String mapping : MAPPINGS) {
            verify(inputManager, times(1)).addMapping(eq(mapping), any(Trigger[].class));
        }
        verify(inputManager, times(1)).addListener(any(), eq(MAPPINGS));
    }

    @Test
    @DisplayName("A second handler re-adds no mapping, but does re-attach its own listener")
    void secondHandlerReusesTheMappings() {
        // Prepare - the engine already carries every mapping (installed by the first handler / an
        // earlier run of the level)
        when(inputManager.hasMapping(any())).thenReturn(true);

        // Execute - the respawn's fresh handler
        final PlayerInputHandler respawned = new PlayerInputHandler(gameEngine);
        respawned.init();

        // Verify - not one mapping is re-added (this is what silences the "twice" warnings on restart)
        verify(inputManager, never()).addMapping(any(), any(Trigger[].class));
        // ...but the new handler still becomes the active listener so the respawned player gets input
        verify(inputManager, times(1)).addListener(eq(respawned), eq(MAPPINGS));
    }

    @Test
    @DisplayName("Retiring a handler removes its listener and leaves the shared mappings alone")
    void detachInputRemovesOnlyTheListener() {
        when(inputManager.hasMapping(any())).thenReturn(true);
        final PlayerInputHandler handler = new PlayerInputHandler(gameEngine);
        handler.init();

        // Execute
        handler.detachInput();

        // Verify - the listener is dropped; the engine-owned mappings are never deleted
        verify(inputManager, times(1)).removeListener(handler);
        verify(inputManager, never()).deleteMapping(any());
    }
}
