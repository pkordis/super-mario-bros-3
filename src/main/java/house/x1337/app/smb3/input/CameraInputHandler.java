package house.x1337.app.smb3.input;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;

import java.util.ArrayList;
import java.util.List;

/**
 * Input handler for camera navigation.
 * Registers arrow-key and modifier mappings and maintains per-key
 * {@link BooleanKeyHandler} states that are queried by the
 * {@code CameraNavigationControl} in the engine package.
 */
// TODO: not needed
public final class CameraInputHandler implements ActionListener {

    public static final String HANDLER_UP = "CAMERA_UP";
    public static final String HANDLER_DOWN = "CAMERA_DOWN";
    public static final String HANDLER_LEFT = "CAMERA_LEFT";
    public static final String HANDLER_RIGHT = "CAMERA_RIGHT";
    public static final String HANDLER_ACCELERATE = "CAMERA_ACCELERATE";
    public static final String HANDLER_EXIT = "CAMERA_EXIT";

    private final List<BooleanKeyHandler> handlers = new ArrayList<>();
    private Runnable exitAction;

    public CameraInputHandler() {
        handlers.add(new BooleanKeyHandler(HANDLER_UP));
        handlers.add(new BooleanKeyHandler(HANDLER_DOWN));
        handlers.add(new BooleanKeyHandler(HANDLER_LEFT));
        handlers.add(new BooleanKeyHandler(HANDLER_RIGHT));
        handlers.add(new BooleanKeyHandler(HANDLER_ACCELERATE));
        handlers.add(new BooleanKeyHandler(HANDLER_EXIT));
    }

    /**
     * Installs key mappings into the given {@link InputManager}.
     */
    public void install(final InputManager inputManager) {
        inputManager.addMapping(HANDLER_UP, new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping(HANDLER_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping(HANDLER_LEFT, new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping(HANDLER_RIGHT, new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping(HANDLER_ACCELERATE, new KeyTrigger(KeyInput.KEY_Z));
        inputManager.addMapping(HANDLER_EXIT, new KeyTrigger(KeyInput.KEY_ESCAPE));

        inputManager.addListener(this,
                HANDLER_UP, HANDLER_DOWN, HANDLER_LEFT, HANDLER_RIGHT,
                HANDLER_ACCELERATE, HANDLER_EXIT);
    }

    public void setExitAction(final Runnable exitAction) {
        this.exitAction = exitAction;
    }

    public BooleanKeyHandler getHandler(final String key) {
        for (final BooleanKeyHandler handler : handlers) {
            if (handler.getKey().equals(key)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} when any directional key is active.
     */
    public boolean isMoving() {
        for (final BooleanKeyHandler handler : handlers) {
            final String k = handler.getKey();
            if (!k.equals(HANDLER_ACCELERATE) && !k.equals(HANDLER_EXIT) && handler.isActive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onAction(final String name, final boolean isPressed, final float tpf) {
        final BooleanKeyHandler handler = getHandler(name);
        if (handler != null) {
            handler.onAction(isPressed);
        }

        // Handle exit immediately on press
        if (HANDLER_EXIT.equals(name) && isPressed && exitAction != null) {
            exitAction.run();
        }
    }
}


