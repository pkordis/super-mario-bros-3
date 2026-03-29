package house.x1337.app.smb3.input;

/**
 * Manages the boolean state of a single input key.
 * Tracks whether the key is currently active (pressed) and whether
 * the press has already been handled (for single-fire actions).
 */
// TODO: not needed
public final class BooleanKeyHandler {

    private final String key;
    private boolean active;
    private boolean hasBeenHandled;

    public BooleanKeyHandler(final String key) {
        this.key = key;
    }

    /**
     * Called by the input listener when the key state changes.
     *
     * @param pressed {@code true} on key-down, {@code false} on key-up
     */
    public void onAction(final boolean pressed) {
        if (pressed) {
            final boolean wasActive = this.active;
            this.active = true;
            if (!wasActive) {
                this.hasBeenHandled = false;
            }
        } else {
            this.active = false;
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isActiveButNotHandled() {
        if (hasBeenHandled) {
            return false;
        }
        return active;
    }

    public void setHasBeenHandled(final boolean hasBeenHandled) {
        this.hasBeenHandled = hasBeenHandled;
    }

    public String getKey() {
        return key;
    }
}

