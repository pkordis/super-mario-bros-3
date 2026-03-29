package house.x1337.app.smb3.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static house.x1337.app.smb3.game.Physics.Parameter.FRICTION;
import static house.x1337.app.smb3.game.Physics.Parameter.GRAVITY;
import static house.x1337.app.smb3.game.Physics.Parameter.JUMP_SUSTAIN_TIME;
import static house.x1337.app.smb3.game.Physics.Parameter.JUMP_VELOCITY;
import static house.x1337.app.smb3.game.Physics.Parameter.RUN_ACCELERATION;
import static house.x1337.app.smb3.game.Physics.Parameter.RUN_AIR_RESISTANCE;
import static house.x1337.app.smb3.game.Physics.Parameter.RUN_MAX_SPEED;
import static house.x1337.app.smb3.game.Physics.Parameter.WALK_ACCELERATION;
import static house.x1337.app.smb3.game.Physics.Parameter.WALK_AIR_RESISTANCE;
import static house.x1337.app.smb3.game.Physics.Parameter.WALK_MAX_SPEED;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;

/**
 * Thread-safe singleton holding every tweakable physics value in the game,
 * backed by a {@link ConcurrentHashMap} keyed on {@link Physics.Parameter}.
 */
public final class Physics {

    /**
     * Enumeration of all tweakable physics parameters.
     */
    public enum Parameter {
        GRAVITY,
        WALK_ACCELERATION,
        RUN_ACCELERATION,
        WALK_MAX_SPEED,
        RUN_MAX_SPEED,
        FRICTION,
        WALK_AIR_RESISTANCE,
        RUN_AIR_RESISTANCE,
        JUMP_VELOCITY,
        JUMP_SUSTAIN_TIME
    }

    private static final Physics INSTANCE = new Physics();

    private static final Map<Parameter, Float> DEFAULTS = ofEntries(
        entry(GRAVITY, 3380f),
        entry(WALK_ACCELERATION, 846f),
        entry(RUN_ACCELERATION, 422f),
        entry(WALK_MAX_SPEED, 390f),
        entry(RUN_MAX_SPEED, 780f),
        entry(FRICTION, 592f),
        entry(WALK_AIR_RESISTANCE, 100f),
        entry(RUN_AIR_RESISTANCE, 50f),
        entry(JUMP_VELOCITY, -742f),
        entry(JUMP_SUSTAIN_TIME, 310f)
    );

    private final ConcurrentMap<Parameter, Float> values = new ConcurrentHashMap<>();

    public static Physics get() {
        return INSTANCE;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the current value of the given physics' parameter.
     */
    public float get(final Parameter param) {
        return values.getOrDefault(param, DEFAULTS.get(param));
    }

    /**
     * Sets the value of the given physics' parameter.
     */
    public void set(final Parameter param, final float value) {
        values.put(param, value);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Reset every parameter to its default value. */
    public void resetAll() {
        for (final Parameter param : Parameter.values()) {
            values.put(param, DEFAULTS.get(param));
        }
    }
}
