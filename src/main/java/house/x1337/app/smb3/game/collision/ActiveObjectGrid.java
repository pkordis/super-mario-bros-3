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
    /**
     * How far above an object's top the player's reference Y must be for contact to count as a stomp
     * rather than a body hit — the ROM's {@code Player_HitEnemy} @ PRG000_D218, which loads {@code #19}
     * for an ordinary object and compares {@code Objects_Y - 19} against {@code Player_Y}.
     *
     * <p>The ROM picks this per object: {@code 17} for a pile-driver microgoomba and a hopping Cheep
     * Cheep, {@code 8} when the object is giant, {@code 19} for everything else. Only ordinary-sized
     * enemies exist here, so the ordinary value is the only one needed; it becomes a per-object property
     * the moment a giant or microgoomba does.
     */
    private static final double STOMP_RANGE_PIXELS = 19.0;

    private final Map<Long, List<T>> cellsByKey = new HashMap<>();
    private final EnemySpawner enemySpawner;

    /**
     * This tick's {@link ActiveLevelObject#bouncesOffOtherObjects() bouncing} objects, in insertion
     * order — the candidate set for {@link #resolveObjectToObjectBumps()}. Kept alongside the buckets so
     * that pass iterates only the handful of objects that can bump, rather than walking every cell.
     */
    private final List<T> bouncingObjects = new ArrayList<>();

    /**
     * Empties every bucket. Call once at the start of each tick before re-inserting.
     */
    public void clear() {
        cellsByKey.clear();
        bouncingObjects.clear();
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
        if (object.bouncesOffOtherObjects()) {
            bouncingObjects.add(object);
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
     * Turns every pair of overlapping {@link ActiveLevelObject#bouncesOffOtherObjects() bouncing}
     * objects away from each other, but only where they meet <b>side-on</b> — the ROM's
     * {@code Object_BumpOffOthers} (dasm prg000 @ PRG000_CC2B), which is what stops a row of walking
     * enemies from drifting through one another.
     *
     * <p>Run as its own phase, after every manager has moved and re-inserted its objects and before the
     * player pass. It cannot be folded into the walk itself: an enemy deciding mid-move whether a
     * neighbour blocks it would be querying a half-filled grid, seeing only the objects whose managers
     * happened to run first and missing the rest entirely. That is the same reason the tail strike is
     * resolved from here.
     *
     * <p>Consequently the bump is detected just <em>after</em> the overlap rather than instead of it, so
     * the two objects interpenetrate briefly — at a walking pace of half a pixel per tick, by under a
     * pixel — and separate on the following tick. The ROM behaves the same way: {@code Object_Move} has
     * already run, and {@code Object_BumpOffOthers} only rewrites the facing bit, never the position.
     *
     * <p>Each object is resolved against every neighbour independently and told to face <em>away</em>
     * from it, so the outcome does not depend on iteration order and a pair that spawns already
     * overlapping still separates instead of flipping in lockstep.
     */
    public void resolveObjectToObjectBumps() {
        for (final T object : bouncingObjects) {
            final AxisAlignedBoundingBox bounds = object.getBounds();
            for (final T other : query(bounds)) {
                if (other == object || !other.bouncesOffOtherObjects()) {
                    continue;
                }
                final AxisAlignedBoundingBox otherBounds = other.getBounds();
                if (bounds.intersects(otherBounds) && isSideContact(bounds, otherBounds)) {
                    object.onSideCollisionWith(other);
                }
            }
        }
    }

    /**
     * Whether two overlapping boxes meet on a vertical face rather than a horizontal one, by the same
     * <b>least-overlapping-axis</b> rule {@link #dispatchDirectionalPlayerCollision} uses: the shallower
     * axis is the one they actually came together along.
     *
     * <p>This is what confines the bump to left/right contact. Two enemies walking into each other share
     * a full 16px of height, so X is far shallower and it counts as a side hit; one landing on another's
     * head overlaps almost fully in X and barely in Y, so it does not.
     *
     * @param bounds      the object being resolved
     * @param otherBounds the neighbour it overlaps
     * @return {@code true} if the contact is left/right rather than above/below
     */
    private static boolean isSideContact(
        final AxisAlignedBoundingBox bounds,
        final AxisAlignedBoundingBox otherBounds
    ) {
        // Per-edge overlap depths (all positive: the boxes already intersect).
        final double topPenetration = bounds.bottom() - otherBounds.top();
        final double bottomPenetration = otherBounds.bottom() - bounds.top();
        final double leftPenetration = bounds.right() - otherBounds.left();
        final double rightPenetration = otherBounds.right() - bounds.left();
        return min(leftPenetration, rightPenetration) < min(topPenetration, bottomPenetration);
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
                    dispatchDirectionalPlayerCollision(object, levelScenePlayer);
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
     * <p>The split is <b>purely vertical</b>, and deliberately so: the ROM's {@code Player_HitEnemy}
     * (dasm prg000 @ PRG000_D218) decides a stomp from one comparison, {@code Objects_Y - 19} against
     * {@code Player_Y}, and never consults the horizontal axis at all. Once the boxes overlap, the only
     * question is whether the player's reference top sits at least {@value #STOMP_RANGE_PIXELS}px above
     * the object's top:
     *
     * <ul>
     *   <li><b>From above</b> ({@code onCollisionFromAbove}) — a stomp: within stomp range and the player
     *       not rising ({@code DY >= 0}).</li>
     *   <li><b>From below</b> ({@code onCollisionFromBelow}) — too deep to be a stomp, and the player is
     *       rising into the object.</li>
     *   <li><b>Overlap</b> ({@code onPlayerOverlap}) — too deep to be a stomp and not rising: the body
     *       hit the ROM answers by hurting the player.</li>
     * </ul>
     *
     * <p>Note the threshold measures from {@link house.x1337.app.smb3.model.game.player.PlayerPosition}'s
     * Y, <b>not</b> from the hitbox top. That reference is the same for either size — the feet are always
     * at {@code Y + 32} — so one constant covers large and small Mario alike, which the ROM calls out
     * explicitly ({@code Player_Y} is "near the hat" when Super and "roughly 16 pixels above" the head
     * when small). Measuring from the hitbox top instead would silently make the two sizes stomp at
     * different depths.
     *
     * <p>This replaced a least-overlapping-axis test plus a 6px minimum sink depth, neither of which the
     * ROM has. Together they made the stomp progressively harder the faster the player fell — a deeper
     * vertical overlap demanded an equally deep horizontal one — so a stomp only registered near the
     * enemy's centre, and a player landing across two adjacent enemies got roughly half the needed
     * overlap on each and stomped neither. The vertical threshold subsumes what the axis test was for:
     * running into an enemy's side puts the player's feet level with its feet, far too deep to qualify.
     *
     * @param object       the object the player is overlapping this tick
     * @param player       the colliding player
     */
    private void dispatchDirectionalPlayerCollision(
        final ActiveLevelObject object,
        final LevelScenePlayer player
    ) {
        final double objectTop = object.getBounds().top();
        final boolean withinStompRange = player.getPosition().getY() <= objectTop - STOMP_RANGE_PIXELS;
        final boolean playerRising = player.getPosition().getDY() < 0;

        if (withinStompRange) {
            if (!playerRising) {
                object.onCollisionFromAbove(player);
            }
            // Rising while still clear of the object: the player is leaving it, most often on the very
            // bounce a stomp just gave them. Nothing to dispatch.
            return;
        }
        if (playerRising) {
            object.onCollisionFromBelow(player);
            return;
        }
        object.onPlayerOverlap(player);
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
