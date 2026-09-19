package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.EnemySpawner;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.floor;
import static java.lang.Math.min;

/**
 * A uniform-grid (spatial-hash) broad-phase for dynamic {@link ActiveLevelObject}s, in sprite-pixel
 * space. It buckets objects into fixed-size square cells so a collision query only tests objects in
 * the handful of cells overlapping a region, instead of every object in the scene.
 *
 * <p>Why a grid, and why this one: player-vs-object collision is already cheap — there are only a
 * couple of players, so testing every object against them is linear. The quadratic cost appears
 * with <b>object-vs-object</b> interactions (e.g. a shell bowling through a row of enemies) once
 * there are hundreds of active objects. A uniform grid is the natural fit for a tile-based world:
 * cells align to the tile lattice and re-bucketing is O(objects). This class serves both cases —
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
@Prototype
@RequiredArgsConstructor
public final class ActiveObjectGrid<T extends ActiveLevelObject> {
    private static final double DIRECTIONAL_HIT_MIN_OVERLAP_PIXELS = 6.0;

    private final Map<Long, List<T>> cellsByKey = new HashMap<>();
    private final EnemySpawner enemySpawner;

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

    /**
     * Spawns the enemies that were placed in the level by the author. This is a one-time call at
     * level load time.
     */
    public void spawnPlacedEnemies() {
        enemySpawner.spawn();
    }

    /**
     * Converts a sprite-pixel coordinate to the index of the cell it falls in. The grid is infinite
     * in all directions, so negative coordinates yield negative indices.
     */
    private int cellIndex(final double coordinate) {
        return (int) floor(coordinate / TILE_SPRITE_SIZE);
    }

    /**
     * Packs a signed 2D cell coordinate into a single long key (X in the high 32 bits, Y in the
     * low 32), so negative indices (objects left of / above the origin) hash distinctly.
     */
    private static long keyOf(final int cellX, final int cellY) {
        return (((long) cellX) << 32) | (cellY & 0xFFFFFFFFL);
    }

    /**
     * The per-player pass over this tick's live objects: body collisions first, then the tail strike.
     *
     * <p>The tail is resolved here, and not from the player's own update, because the ROM resolves it
     * in the object loop — each object runs {@code Object_DoStateAction} and is handed straight to
     * {@code Object_HitByTailOrBouncer} in the same iteration (dasm prg000 @ PRG000_C9B6, inside the
     * {@code DEX / BPL PRG000_C975} loop). Running it at this point is the equivalent guarantee: every
     * object has already moved and been re-inserted this tick, so no strike is ever decided against a
     * stale position or against an entry a manager has since retired. Driving it from the player's
     * update instead would read the grid as the managers left it on the previous tick — enough to miss
     * an enemy that has just stepped into the box, or strike one that has just stepped out.
     *
     * <p>The swing state read from the player is nonetheless this tick's: its counter advanced during
     * {@code updateFrame}, which the engine runs before this pass, exactly as the ROM's player routine
     * precedes its object loop. The player owns every tail semantic — which frames strike, and where
     * the box sits; this class only picks the moment and dispatches.
     *
     * @param levelScenePlayers the players to test
     */
    public void resolveActiveObjectCollisions(final List<LevelScenePlayer> levelScenePlayers) {
        for (final LevelScenePlayer levelScenePlayer : levelScenePlayers) {
            final AxisAlignedBoundingBox playerBounds = levelScenePlayer.getObjectCollisionBounds();
            for (final ActiveLevelObject object : query(playerBounds)) {
                if (!object.intersects(playerBounds)) {
                    continue;
                }
                object.onCollisionWith(levelScenePlayer);
                if (object.resolvesDirectionalPlayerCollision()) {
                    dispatchDirectionalPlayerCollision(object, levelScenePlayer, playerBounds);
                }
            }
            if (levelScenePlayer.isTailAttackStriking()) {
                resolveTailAttack(levelScenePlayer, levelScenePlayer.getTailAttackBounds());
            }
        }
    }

    /**
     * Resolves which side of an {@link ActiveLevelObject} the player struck and dispatches the matching
     * directional {@link house.x1337.app.smb3.game.object.level.LevelObject} method — the active-object
     * mirror of {@code StaticEnvironmentCollisionGrid}'s terrain dispatch, for the objects that opt in
     * via {@link ActiveLevelObject#resolvesDirectionalPlayerCollision()} (the enemies).
     *
     * <p>The side is the box's <b>least-overlapping axis</b> (the minimum-translation direction), so a
     * collision counts as vertical only while the player is more overlapped along X than Y — i.e. it is
     * genuinely on top of / underneath the object, not merely brushing a top corner while running into
     * the side. That axis then splits by which edge is shallower and by the player's vertical motion:
     *
     * <ul>
     *   <li><b>From above</b> ({@code onCollisionFromAbove}) — a stomp: a vertical hit whose shallow edge
     *       is the object's top, the player descending ({@code DY >= 0}), and the feet already
     *       {@value #DIRECTIONAL_HIT_MIN_OVERLAP_PIXELS}px into that top. The overlap gate is the fix for
     *       the stomp firing on the first grazing frame: it waits until the player has genuinely sunk
     *       onto the object, so a small — but real — overlap is required, uniformly for every enemy.</li>
     *   <li><b>From below</b> ({@code onCollisionFromBelow}) — a vertical hit whose shallow edge is the
     *       object's bottom while the player is rising ({@code DY < 0}).</li>
     *   <li><b>Overlap</b> ({@code onPlayerOverlap}) — a side hit (X is the shallower axis).</li>
     * </ul>
     *
     * <p>A vertical hit that is not yet {@value #DIRECTIONAL_HIT_MIN_OVERLAP_PIXELS}px deep, or whose
     * direction disagrees with the player's motion, dispatches nothing this tick and waits for the next.
     *
     * @param object       the object the player is overlapping this tick
     * @param player       the colliding player
     * @param playerBounds the player's object-collision hitbox for this tick
     */
    private void dispatchDirectionalPlayerCollision(
        final ActiveLevelObject object,
        final LevelScenePlayer player,
        final AxisAlignedBoundingBox playerBounds
    ) {
        final AxisAlignedBoundingBox objectBounds = object.getBounds();

        // Per-edge overlap depths (all positive: the boxes already intersect).
        final double topPenetration = playerBounds.bottom() - objectBounds.top();
        final double bottomPenetration = objectBounds.bottom() - playerBounds.top();
        final double leftPenetration = playerBounds.right() - objectBounds.left();
        final double rightPenetration = objectBounds.right() - playerBounds.left();
        final double verticalPenetration = min(topPenetration, bottomPenetration);
        final double horizontalPenetration = min(leftPenetration, rightPenetration);

        // Shallower along X than Y: a side hit, whichever way the player is moving.
        if (horizontalPenetration < verticalPenetration) {
            object.onPlayerOverlap(player);
            return;
        }

        final boolean playerDescending = player.getPosition().getDY() >= 0;
        if (topPenetration <= bottomPenetration) {
            // The player's lower part is the leading edge — a landing on the object's top. Only a
            // descending player that has already overlapped by the minimum counts; otherwise wait.
            if (playerDescending && topPenetration >= DIRECTIONAL_HIT_MIN_OVERLAP_PIXELS) {
                object.onCollisionFromAbove(player);
            }
            return;
        }
        // The player's upper part is the leading edge — rising into the object's underside.
        if (!playerDescending && bottomPenetration >= DIRECTIONAL_HIT_MIN_OVERLAP_PIXELS) {
            object.onCollisionFromBelow(player);
        }
    }

    /**
     * Dispatches {@link house.x1337.app.smb3.game.object.level.LevelObject#onTailAttack} to every
     * active object whose bounds the player's tail hitbox intersects. The broadphase narrows the
     * candidate set; each survivor is confirmed with a precise {@code intersects} narrowphase, exactly
     * as {@link #resolveActiveObjectCollisions(List)} does for body collisions.
     *
     * @param levelScenePlayer the striking player
     * @param tailBounds       the player's tail hitbox for this tick
     */
    public void resolveTailAttack(final LevelScenePlayer levelScenePlayer, final AxisAlignedBoundingBox tailBounds) {
        for (final ActiveLevelObject object : query(tailBounds)) {
            if (object.intersects(tailBounds)) {
                object.onTailAttack(levelScenePlayer);
            }
        }
    }
}
