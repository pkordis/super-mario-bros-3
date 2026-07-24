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
    public static final String HANDLER_EXIT = "PLAYER_EXIT";

    /** Keys currently held down. */
    private final Set<String> activeKeys = new HashSet<>();

    /** Keys whose press has already been consumed (one-shot actions). */
    private final Set<String> handledKeys = new HashSet<>();

    /**
     * Installs key mappings into the given {@link InputManager}.
     */
    @PostConstruct
    public void init() {
        final InputManager inputManager = gameEngine.getInputManager();
        inputManager.addMapping(HANDLER_UP, new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping(HANDLER_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping(HANDLER_LEFT, new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping(HANDLER_RIGHT, new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping(HANDLER_JUMP, new KeyTrigger(KeyInput.KEY_X));
        inputManager.addMapping(HANDLER_RUN, new KeyTrigger(KeyInput.KEY_Z));
        inputManager.addMapping(HANDLER_SIZE_TOGGLE, new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping(HANDLER_LAYER_TOGGLE, new KeyTrigger(KeyInput.KEY_L));

        inputManager.addListener(this,
                HANDLER_UP, HANDLER_DOWN, HANDLER_LEFT, HANDLER_RIGHT,
                HANDLER_JUMP, HANDLER_RUN, HANDLER_SIZE_TOGGLE, HANDLER_LAYER_TOGGLE);
        //,
        //        HANDLER_EXIT);
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
        } else {
            activeKeys.remove(name);
        }
    }
}
