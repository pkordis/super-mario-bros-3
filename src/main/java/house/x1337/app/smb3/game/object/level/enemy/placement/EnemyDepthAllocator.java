package house.x1337.app.smb3.game.object.level.enemy.placement;

import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.GameConstants.Z_STEP_BETWEEN_ENEMIES;

/**
 * Hands each enemy spawned from a placement its own base depth, so its body and its extra quads (wings,
 * waking feet) sort as one unit against every other enemy.
 */
public final class EnemyDepthAllocator {
    public static final int SLOT_COUNT = 64;

    private int nextSlot;

    public static float baseDepthOfSlot(final int slot) {
        return Z_DEPTH_ENEMY + slot * Z_STEP_BETWEEN_ENEMIES;
    }

    public float next() {
        final float depth = baseDepthOfSlot(nextSlot);
        nextSlot = (nextSlot + 1) % SLOT_COUNT;
        return depth;
    }

    public void reset() {
        nextSlot = 0;
    }
}
