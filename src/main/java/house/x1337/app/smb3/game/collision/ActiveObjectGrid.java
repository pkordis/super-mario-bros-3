package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.floor;

/**
 * A uniform-grid (spatial-hash) broadphase for dynamic {@link ActiveLevelObject}s, in sprite-pixel
 * space. It buckets objects into fixed-size square cells so a collision query only tests objects in
 * the handful of cells overlapping a region, instead of every object in the scene.
 *
 * <p>Why a grid, and why this one: player-vs-object collision is already cheap — there are only a
 * couple of players, so testing every object against them is linear. The quadratic cost appears
 * with <b>object-vs-object</b> interactions (e.g. a shell bowling through a row of enemies) once
 * there are hundreds of active objects. A uniform grid is the natural fit for a tile-based world:
 * cells align to the tile lattice and rebucketing is O(objects). This class serves both cases —
 * {@link #query(AxisAlignedBoundingBox)} returns the candidates near any box, which a player hitbox or another
 * object's (expanded) box can use alike.
 *
 * <p><b>Lifecycle (two-phase, per tick):</b> {@link #clear()} then {@link #insert(ActiveLevelObject)}
 * every active object, and only then {@link #query(AxisAlignedBoundingBox)}. Positions are read at insert time, so
 * all inserts must complete before any query. This is deliberately distinct from the static terrain
 * {@code CollisionGrid}, which holds tile-aligned solids and is rebuilt only when the level mutates.
 *
 * <p>Not thread-safe; it is driven from the single simulation thread.
 *
 * @param <T> the concrete active-object type this grid indexes
 */
public final class ActiveObjectGrid<T extends ActiveLevelObject> {
    private final Map<Long, List<T>> cellsByKey = new HashMap<>();
    private final int cellSize;

    /**
     * @param cellSize edge length of each square cell, in sprite-pixels. A tile (16) is a sensible
     *                 default; larger cells mean fewer buckets but more candidates per query.
     */
    public ActiveObjectGrid(final int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive, was " + cellSize);
        }
        this.cellSize = cellSize;
    }

    /**
     * Empties every bucket. Call once at the start of each tick before re-inserting.
     */
    public void clear() {
        cellsByKey.clear();
    }

    /**
     * Buckets an object into every cell its {@link ActiveLevelObject#getBounds() bounds} overlap.
     *
     * @param object the object to index this tick
     */
    public void insert(final T object) {
        final AxisAlignedBoundingBox bounds = object.getBounds();
        final int minCellX = cellIndex(bounds.left());
        final int maxCellX = cellIndex(bounds.right());
        final int minCellY = cellIndex(bounds.top());
        final int maxCellY = cellIndex(bounds.bottom());
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                cellsByKey
                    .computeIfAbsent(keyOf(cellX, cellY), ignored -> new ArrayList<>())
                    .add(object);
            }
        }
    }

    /**
     * Returns the distinct objects bucketed in any cell the region overlaps — the broadphase
     * candidate set. Callers must still run a precise {@link AxisAlignedBoundingBox#intersects(AxisAlignedBoundingBox)} narrowphase on
     * each candidate, since sharing a cell does not guarantee overlap.
     *
     * @param region the box to gather candidates around (e.g. a player hitbox)
     * @return a fresh list of candidate objects, each appearing once
     */
    public List<T> query(final AxisAlignedBoundingBox region) {
        final int minCellX = cellIndex(region.left());
        final int maxCellX = cellIndex(region.right());
        final int minCellY = cellIndex(region.top());
        final int maxCellY = cellIndex(region.bottom());

        final List<T> candidates = new ArrayList<>();
        final Set<T> alreadyAdded = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                final List<T> bucket = cellsByKey.get(keyOf(cellX, cellY));
                if (bucket == null) {
                    continue;
                }
                for (final T candidate : bucket) {
                    // An object spanning several cells is listed in each; dedup by identity so the
                    // caller runs its narrowphase test once per object.
                    if (alreadyAdded.add(candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return candidates;
    }

    private int cellIndex(final double coordinate) {
        return (int) floor(coordinate / cellSize);
    }

    /**
     * Packs a signed 2D cell coordinate into a single long key (X in the high 32 bits, Y in the
     * low 32), so negative indices (objects left of / above the origin) hash distinctly.
     */
    private static long keyOf(final int cellX, final int cellY) {
        return (((long) cellX) << 32) | (cellY & 0xFFFFFFFFL);
    }
}
