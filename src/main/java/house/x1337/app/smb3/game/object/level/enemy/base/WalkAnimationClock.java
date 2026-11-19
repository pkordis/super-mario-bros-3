package house.x1337.app.smb3.game.object.level.enemy.base;

import lombok.Getter;

/**
 * Paces an enemy's walk cycle. Each tick adds a rate, in 256ths of a step; a full step advances {@link #getSteps()},
 * which wraps at {@value #STEP_WRAP}, and every {@value #STEPS_PER_WALK_FRAME} steps the walk frame changes. At
 * {@link GroundWalkingEnemy#FLAT_WALK_ANIMATION_RATE} that is a new frame every 8 ticks. See {@code kb/slopes.md},
 * "Walk animation".
 */
public final class WalkAnimationClock {
    static final int FULL_STEP = 256;
    static final int STEP_WRAP = 256;
    static final int STEPS_PER_WALK_FRAME = 4;

    private int fraction;
    @Getter
    private int steps;

    /**
     * @param rate the share of a step to add, in 256ths
     * @return whether the walk frame changes this tick
     */
    public boolean advance(final int rate) {
        fraction += rate;
        if (fraction < FULL_STEP) {
            return false;
        }
        fraction -= FULL_STEP;
        steps = (steps + 1) % STEP_WRAP;
        return steps % STEPS_PER_WALK_FRAME == 0;
    }
}
