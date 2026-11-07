package house.x1337.app.smb3.input;

import com.jme3.input.InputManager;
import house.x1337.app.smb3.game.engine.GameEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A respawned player's handler takes over the pad as the previous handler left it. jME reports only press and release
 * edges, so without this a key held through a restart or a pipe trip would read as up until pressed again.
 */
class PlayerInputHandlerCarryOverTest {
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getInputManager()).thenReturn(mock(InputManager.class));
    }

    @Test
    @DisplayName("Keys held on the previous handler are held on the new one")
    void heldKeysStayHeld() {
        // Prepare - running right with the P-meter full
        final PlayerInputHandler previous = new PlayerInputHandler(gameEngine);
        previous.onAction(HANDLER_RIGHT, true, 0f);
        previous.onAction(HANDLER_RUN, true, 0f);
        final PlayerInputHandler next = new PlayerInputHandler(gameEngine);

        // Execute
        next.carryOverFrom(previous);

        // Verify
        assertThat(next.isActive(HANDLER_RIGHT)).isTrue();
        assertThat(next.isActive(HANDLER_RUN)).isTrue();
        assertThat(next.isActive(HANDLER_LEFT)).isFalse();
    }

    @Test
    @DisplayName("A press already acted on stays used, and the next press after a release counts")
    void consumedPressesStayConsumed() {
        // Prepare - A held since a jump the previous player made
        final PlayerInputHandler previous = new PlayerInputHandler(gameEngine);
        previous.onAction(HANDLER_JUMP, true, 0f);
        assertThat(previous.consumePress(HANDLER_JUMP)).isTrue();
        final PlayerInputHandler next = new PlayerInputHandler(gameEngine);

        // Execute
        next.carryOverFrom(previous);

        // Verify
        assertThat(next.isActive(HANDLER_JUMP)).as("Still held").isTrue();
        assertThat(next.consumePress(HANDLER_JUMP)).as("No second jump from the same press").isFalse();
        next.onAction(HANDLER_JUMP, false, 0f);
        next.onAction(HANDLER_JUMP, true, 0f);
        assertThat(next.consumePress(HANDLER_JUMP)).as("A new press").isTrue();
    }

    @Test
    @DisplayName("A key released on the previous handler is up on the new one, whatever the new one held before")
    void releasedKeysAreUp() {
        // Prepare
        final PlayerInputHandler previous = new PlayerInputHandler(gameEngine);
        previous.onAction(HANDLER_RIGHT, true, 0f);
        previous.onAction(HANDLER_RIGHT, false, 0f);
        final PlayerInputHandler next = new PlayerInputHandler(gameEngine);
        next.onAction(HANDLER_LEFT, true, 0f);

        // Execute
        next.carryOverFrom(previous);

        // Verify
        assertThat(next.isActive(HANDLER_RIGHT)).isFalse();
        assertThat(next.isActive(HANDLER_LEFT)).isFalse();
    }
}
