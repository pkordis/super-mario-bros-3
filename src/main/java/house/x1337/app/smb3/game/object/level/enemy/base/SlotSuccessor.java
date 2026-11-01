package house.x1337.app.smb3.game.object.level.enemy.base;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;

/**
 * The enemy a {@link SlotPredecessor} turns into: it takes over the predecessor's slot rather than
 * spawning as a new object.
 */
public interface SlotSuccessor extends EnemyLevelObject {
    void adoptSlotOf(SlotPredecessor predecessor, int hitCooldownTicks);

    default void replace(
        final SlotPredecessor predecessor,
        final int hitCooldownTicks
    ) {
        adoptSlotOf(predecessor, hitCooldownTicks);
        setBaseDepth(predecessor.getBaseDepth());
        final ActiveObjectGrid<ActiveLevelObject> activeObjectGrid = getGameEngine().getActiveObjectGrid();
        activeObjectGrid.spawnEnemy(this);
        activeObjectGrid.handOverPlacement(predecessor, this);
    }
}
