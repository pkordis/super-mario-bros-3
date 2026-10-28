package house.x1337.app.smb3.input;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.event.GameEventBusAware;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.player.level.LevelScenePlayerActionEventListener;
import house.x1337.app.smb3.model.event.LevelScenePlayerSwitchedLayer;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Input handler for player movement.
 * Registers player key mappings and maintains per-key pressed/consumed
 * state that is queried by the {@code Player} physics control each tick.
 */
@Prototype
@RequiredArgsConstructor
public final class PlayerInputHandler
    implements
        ActionListener,
        GameEventBusAware {
    @Getter
    private final String id = UUID.randomUUID().toString();
    private final GameEngine gameEngine;

    public static final String HANDLER_UP = "PLAYER_UP";
    public static final String HANDLER_DOWN = "PLAYER_DOWN";
    public static final String HANDLER_LEFT = "PLAYER_LEFT";
    public static final String HANDLER_RIGHT = "PLAYER_RIGHT";
    public static final String HANDLER_JUMP = "PLAYER_JUMP";
    public static final String HANDLER_RUN = "PLAYER_RUN";
    public static final String HANDLER_SIZE_TOGGLE = "PLAYER_SIZE_TOGGLE";
    public static final String HANDLER_LAYER_TOGGLE = "PLAYER_LAYER_TOGGLE";
    public static final String HANDLER_CAPTURE_TOGGLE = "PLAYER_CAPTURE_TOGGLE";
    public static final String HANDLER_RESET = "LEVEL_RESET";
    public static final String HANDLER_EXIT = "PLAYER_EXIT";

    /** Keys currently held down. */
    private final Set<String> activeKeys = new HashSet<>();

    /** Keys whose press has already been consumed (one-shot actions). */
    private final Set<String> handledKeys = new HashSet<>();

    /**
     * Installs key mappings into the given {@link InputManager} and attaches this handler as their
     * listener.
     *
     * <p>The mappings are keyed by name on the engine's single, long-lived {@code InputManager}, so they
     * belong to the {@link GameEngine}, not to any one player. A fresh handler is built every time a
     * player is (re)spawned — notably on the death restart — so the {@code addMapping} calls are guarded
     * with {@link InputManager#hasMapping(String)}: they install once per engine and are skipped on
     * every later handler, which is what stops jME3's "Attempted to add mapping ... twice" warnings on
     * restart. The listener, by contrast, is per-handler and is always (re)attached so this tick's input
     * routes to the current player; the engine drops the previous player's listener when it respawns.
     */
    @PostConstruct
    public void init() {
        final InputManager inputManager = gameEngine.getInputManager();
        registerMapping(inputManager, HANDLER_UP, KeyInput.KEY_UP);
        registerMapping(inputManager, HANDLER_DOWN, KeyInput.KEY_DOWN);
        registerMapping(inputManager, HANDLER_LEFT, KeyInput.KEY_LEFT);
        registerMapping(inputManager, HANDLER_RIGHT, KeyInput.KEY_RIGHT);
        registerMapping(inputManager, HANDLER_JUMP, KeyInput.KEY_X);
        registerMapping(inputManager, HANDLER_RUN, KeyInput.KEY_Z);
        registerMapping(inputManager, HANDLER_SIZE_TOGGLE, KeyInput.KEY_RETURN);
        registerMapping(inputManager, HANDLER_LAYER_TOGGLE, KeyInput.KEY_L);
        registerMapping(inputManager, HANDLER_RESET, KeyInput.KEY_R);

        inputManager.addListener(
            this,
            HANDLER_UP,
            HANDLER_DOWN,
            HANDLER_LEFT,
            HANDLER_RIGHT,
            HANDLER_JUMP,
            HANDLER_RUN,
            HANDLER_SIZE_TOGGLE,
            HANDLER_LAYER_TOGGLE,
            HANDLER_RESET
        );
    }

    /**
     * Adds a key mapping only if the engine's {@code InputManager} does not already carry it. The
     * mappings outlive any single player, so a handler built for a respawn must not re-add them.
     *
     * @param inputManager the engine's shared input manager
     * @param name         the mapping name (a {@code HANDLER_*} constant)
     * @param keyCode      the {@link KeyInput} code to bind
     */
    private static void registerMapping(final InputManager inputManager, final String name, final int keyCode) {
        if (!inputManager.hasMapping(name)) {
            inputManager.addMapping(name, new KeyTrigger(keyCode));
        }
    }

    /**
     * Removes this handler's listener from the engine's {@code InputManager}, leaving the shared key
     * mappings in place (they are owned by the engine, registered once in {@link #init()}). Called when
     * the player this handler drives is retired — on the death restart a fresh handler is built for the
     * respawned player, and without this the retired handler would stay subscribed and keep mutating
     * its own (now unread) key state on every input, accumulating one dead listener per death.
     */
    public void detachInput() {
        gameEngine.getInputManager().removeListener(this);
    }

    /**
     * Returns {@code true} if the key identified by {@code key} is currently held down.
     *
     * @param key one of the {@code HANDLER_*} constants
     * @return whether the key is active (pressed)
     */
    public boolean isActive(final String key) {
        return activeKeys.contains(key);
    }

    /**
     * Returns {@code true} if the key identified by {@code key} was pressed but has not yet
     * been consumed, and marks it as consumed so subsequent calls within the same press
     * return {@code false}.
     *
     * @param key one of the {@code HANDLER_*} constants
     * @return whether a fresh (unconsumed) press is available
     */
    public boolean consumePress(final String key) {
        if (activeKeys.contains(key) && !handledKeys.contains(key)) {
            handledKeys.add(key);
            return true;
        }
        return false;
    }

    @Override
    public void onAction(
        final String name,
        final boolean isPressed,
        final float timePerFrame
    ) {
        if (isPressed) {
            if (!activeKeys.contains(name)) {
                handledKeys.remove(name);
            }
            activeKeys.add(name);
            if (name.equals(HANDLER_LAYER_TOGGLE)) {
                publish(
                    LevelScenePlayerSwitchedLayer
                        .builder()
                        .inputHandlerId(id)
                        .build()
                );
            }
            if (name.equals(HANDLER_RESET)) {
                publish(
                    LevelSceneResetRequested
                        .builder()
                        .inputHandlerId(id)
                        .build()
                );
            }
        } else {
            activeKeys.remove(name);
        }
    }
}
