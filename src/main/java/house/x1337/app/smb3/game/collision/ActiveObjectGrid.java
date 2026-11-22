package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
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
import static house.x1337.app.smb3.model.game.Coordinates.of;
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
 * <p>A stomp needs the player's Y at least {@value #STOMP_RANGE_PIXELS} px above the object's stomp reference.
 * Against a player on the ground, an object can only be stomped while it is in the air, falling slower than
 * {@value #SLOW_FALL_SPEED_LIMIT} sixteenths of a pixel per tick. See kb/slopes.md, "Enemies meeting the player and
 * each other on a slope".
 *
 * @param <T> the concrete active-object type this grid indexes
 */
@Prototype
@RequiredArgsConstructor
public final class ActiveObjectGrid<T extends ActiveLevelObject> {
    private static final int SLOW_FALL_SPEED_LIMIT = 10;
    private static final double STOMP_RANGE_PIXELS = 19.0;

    private final Map<Coordinates, List<T>> cellsByKey = new HashMap<>();
    private final EnemySpawner enemySpawner;

    /**
     * This tick's {@link ActiveLevelObject#bouncesOffOtherObjects() bouncing} objects, in insertion
     * order — the candidate set for {@link #resolveObjectToObjectBumps()}. Kept alongside the buckets so
     * that pass iterates only the handful of objects that can bump, rather than walking every cell.
     */
    private final List<T> bouncingObjects = new ArrayList<>();

    /**
     * This tick's {@link ActiveLevelObject#isKickedHazard() kicked} objects, in insertion order — the
     * candidate killers for {@link #resolveKickedObjectKills()}. Kept alongside {@link #bouncingObjects}
     * and for the same reason: the lethal pass iterates the one or two shells in flight rather than every
     * cell in the grid.
     */
    private final List<T> kickedObjects = new ArrayList<>();

    /**
     * This tick's {@link ActiveLevelObject#isCarriedHazard() carried} objects, in insertion order — the
     * candidates for {@link #resolveCarriedObjectKills()}. There is at most one per player, so this is
     * the shortest of the three lists and the cheapest pass to run.
     */
    private final List<T> carriedObjects = new ArrayList<>();

    /**
     * Empties every bucket. Call once at the start of each tick before re-inserting.
     */
    public void clear() {
        cellsByKey.clear();
        bouncingObjects.clear();
        kickedObjects.clear();
        carriedObjects.clear();
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
                    .computeIfAbsent(of(cellX, cellY), ignored -> new ArrayList<>())
                    .add(object);
            }
        }
        if (object.bouncesOffOtherObjects()) {
            bouncingObjects.add(object);
        }
        if (object.isKickedHazard()) {
            kickedObjects.add(object);
        }
        if (object.isCarriedHazard()) {
            carriedObjects.add(object);
        }
    }

    /**
     * Returns the distinct objects bucketed in any cell the region overlaps — the broad-phase
     * candidate set. Callers must still run a precise {@link AxisAlignedBoundingBox#intersects(AxisAlignedBoundingBox)}
     * narrow-phase on each candidate, since sharing a cell does not guarantee overlap.
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
                final List<T> bucket = cellsByKey.get(of(cellX, cellY));
                if (bucket == null) {
                    continue;
                }
                for (final T candidate : bucket) {
                    // An object spanning several cells is listed in each; dedup by identity so the
                    // caller runs its narrow-phase test once per object.
                    if (alreadyAdded.add(candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * Reads the enemies that were placed in the level by the author. A one-time call at level load
     * time; the enemies themselves spawn as the scroll reaches their columns.
     */
    public void spawnPlacedEnemies() {
        enemySpawner.spawn();
    }

    /**
     * Spawns the opening screen's placed enemies on the first tick, and after that every unspawned one
     * in the scroll's look-ahead column. Call once per tick, before the managers tick.
     */
    public void spawnEnemiesComingIntoView() {
        enemySpawner.spawnPlacementsComingIntoView();
    }

    /**
     * A live enemy has left the screen or the level: its placement may spawn it again.
     *
     * @param enemy the enemy being thrown away
     */
    public void releasePlacementOf(final EnemyLevelObject enemy) {
        enemySpawner.releasePlacementOf(enemy);
    }

    /**
     * An enemy has died: its placement stays spent for the rest of the level.
     *
     * @param enemy the enemy being thrown away
     */
    public void retirePlacementOf(final EnemyLevelObject enemy) {
        enemySpawner.retirePlacementOf(enemy);
    }

    /**
     * Moves a placement to the enemy taking over another's slot (the Para-Goomba's Goomba).
     *
     * @param predecessor the enemy giving up its slot
     * @param successor   the enemy taking it over
     */
    public void handOverPlacement(
        final EnemyLevelObject predecessor,
        final EnemyLevelObject successor
    ) {
        enemySpawner.handOverPlacement(predecessor, successor);
    }

    /**
     * Registers one enemy that came into being mid-level, leaving the rest of the enemy world alone -
     * the counterpart to {@link #spawnPlacedEnemies()}, which is the level-setup pass and resets
     * everything first. Routed through the grid because an object already reaches it through its
     * {@code GameEngine}, and the grid is where the spawner lives.
     *
     * @param enemy the enemy to register with the manager that owns its type
     */
    public void spawnEnemy(final EnemyLevelObject enemy) {
        enemySpawner.spawnOne(enemy);
    }

    /**
     * Converts a sprite-pixel coordinate to the index of the cell it falls in. The grid is infinite
     * in all directions, so negative coordinates yield negative indices.
     */
    private int cellIndex(final double coordinate) {
        return (int) floor(coordinate / TILE_SPRITE_SIZE);
    }

    /**
     * Turns every pair of overlapping {@link ActiveLevelObject#bouncesOffOtherObjects() bouncing} objects away from
     * each other where they meet side-on, on the flat or on a slope. Runs after every object has moved this tick. See
     * kb/slopes.md, "Enemies meeting the player and each other on a slope".
     */
    public void resolveObjectToObjectBumps() {
        for (final T object : bouncingObjects) {
            final AxisAlignedBoundingBox bounds = object.getBounds();
            for (final T other : query(bounds)) {
                if (other == object || !other.bouncesOffOtherObjects()) {
                    continue;
                }
                final AxisAlignedBoundingBox otherBounds = other.getBounds();
                if (bounds.intersects(otherBounds) && meetSideOn(object, other)) {
                    object.onSideCollisionWith(other);
                }
            }
        }
    }

    /**
     * Kills whatever this tick's kicked objects have run into — the lethal half of the object-to-object
     * story, and {@code ActiveObjectGrid}'s motivating use case (see the class Javadoc's shell bowling
     * through a row of enemies).
     *
     * <p><b>Additive, not folded into {@link #resolveObjectToObjectBumps()}.</b> That pass has one
     * semantic — overlapping walkers turn away from each other, nothing else — and the two stay apart in
     * behaviour too: the bump applies to every object, while the kill belongs to the kicked state alone.
     * Teaching the bump to occasionally kill would make a facing rewrite and a death share one call site
     * for no gain; a kicked shell is also not required to {@code bouncesOffOtherObjects()}, so it would
     * not even be in that pass's candidate set.
     *
     * <p>Runs in the same phase and for the same reason: every manager has moved and re-inserted its
     * objects, so no kill is decided against a stale position.
     *
     * <p>The hit test runs on every tick for every kicked object. Running it only on every other tick
     * would merely delay a kill by a frame and make the chain non-deterministic, for nothing in return.
     */
    public void resolveKickedObjectKills() {
        for (final T object : kickedObjects) {
            // Re-read rather than trusting the insert-time snapshot: two shells meeting kill each other,
            // so the second of them may already be dead by the time its own turn comes round.
            if (!object.isKickedHazard()) {
                continue;
            }
            final AxisAlignedBoundingBox bounds = object.getBounds();
            for (final T other : query(bounds)) {
                if (other == object || isNotRammable(other) || !bounds.intersects(other.getBounds())) {
                    continue;
                }
                object.onKickedInto(other);
                if (!object.isKickedHazard()) {
                    // It died on that contact (another kicked shell); it kills nothing more this tick.
                    break;
                }
            }
        }
    }

    /**
     * Kills whatever this tick's <b>carried</b> objects have been walked into, and the carried object with
     * it.
     *
     * <p>Sibling of {@link #resolveKickedObjectKills()} rather than a branch inside it, because they
     * belong to two separate object states and the outcomes differ: a rolling shell survives everything
     * it kills and carries on, a carried one dies with its first victim. Folding them together
     * would put "I live" and "I die" behind one call.
     *
     * <p>The first contact ends the pass for that object — it is dead, so {@link
     * ActiveLevelObject#isCarriedHazard()} is false by the time the next candidate comes round, and the
     * loop breaks on it. A shell walked into two enemies standing shoulder to shoulder therefore takes
     * exactly one of them, whichever the broadphase reaches first.
     */
    public void resolveCarriedObjectKills() {
        for (final T object : carriedObjects) {
            final AxisAlignedBoundingBox bounds = object.getBounds();
            for (final T other : query(bounds)) {
                if (other == object || isNotRammable(other) || !bounds.intersects(other.getBounds())) {
                    continue;
                }
                object.onCarriedInto(other);
                if (!object.isCarriedHazard()) {
                    // It died on that contact, which is the only outcome: nothing else this tick.
                    break;
                }
            }
        }
    }

    /**
     * Whether a kicked or carried object meeting {@code candidate} is a collision at all. A candidate
     * that is not {@link ActiveLevelObject#isKilledByRammedObject() killed by a rammed object} (every
     * powerup, for one) is excluded before any collision is reported.
     *
     * <p>Shared by both lethal passes because it is one test that both object states apply. The
     * collision is not reported at all for an excluded candidate, rather than reported and declined as
     * a kill — so the striker is unaffected too, and a shell rolls straight through a powerup.
     *
     * @param candidate the object a hazard is overlapping
     * @return {@code true} if ramming it resolves to anything
     */
    private boolean isNotRammable(final T candidate) {
        return !candidate.isKilledByRammedObject();
    }

    /**
     * Whether two overlapping objects meet side-on. Two objects on the ground are judged as if they stood level, since
     * on a slope the ground alone puts them at different heights.
     */
    private boolean meetSideOn(
        final T object,
        final T other
    ) {
        final AxisAlignedBoundingBox bounds = object.getBounds();
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        if (!object.isGrounded() || !other.isGrounded()) {
            return isSideContact(bounds, otherBounds);
        }
        final double horizontalOverlap = min(bounds.right() - otherBounds.left(), otherBounds.right() - bounds.left());
        final double levelOverlap = min(bounds.bottom() - bounds.top(), otherBounds.bottom() - otherBounds.top());
        return horizontalOverlap < levelOverlap;
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
     * <p>The tail is resolved here, and not from the player's own update, because the strike belongs to
     * the object pass: each object finishes its own state update and is tested against the tail in the
     * same iteration. Running it at this point gives that guarantee: every object has already moved and
     * been re-inserted this tick, so no strike is ever decided against a
     * stale position or against an entry a manager has since retired. Driving it from the player's
     * update instead would read the grid as the managers left it on the previous tick — enough to miss
     * an enemy that has just stepped into the box, or strike one that has just stepped out.
     *
     * <p>The swing state read from the player is nonetheless this tick's: its counter advanced during
     * {@code updateFrame}, which the engine runs before this pass, so the player update always precedes
     * the object pass. The player owns every tail semantic — which frames strike, and where the box sits;
     * this class only picks the moment and dispatches.
     *
     * @param levelScenePlayers the players to test
     */
    public void resolveActiveObjectCollisions(final List<LevelScenePlayer> levelScenePlayers) {
        for (final LevelScenePlayer levelScenePlayer : levelScenePlayers) {
            final AxisAlignedBoundingBox playerBounds = levelScenePlayer.getObjectCollisionBounds();
            for (final ActiveLevelObject object : query(playerBounds)) {
                if (object.isExpired() || !object.intersects(playerBounds)) {
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
     * Sends the player's contact with an object that opts in to directional collision to the slide, the stomp, the
     * hit from below or the body hit. The stomp is decided from the vertical positions alone. See
     * kb/dynamic-entities.md, "The stomp test", and kb/slopes.md, "Enemies meeting the player and each other on a
     * slope".
     */
    private void dispatchDirectionalPlayerCollision(
        final ActiveLevelObject object,
        final LevelScenePlayer player
    ) {
        if (player.isSliding()) {
            object.onSlidInto(player);
            return;
        }
        if (!object.canBeStomped() || isMetOnTheGround(object, player)) {
            // No stomp possible, and no bounce to let the player rise out of.
            object.onPlayerOverlap(player);
            return;
        }
        final double objectTop = object.getStompReferenceTop();
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
     * Whether a player on the ground meets the object on the ground too, where it cannot be stomped: the object is not
     * in the air falling slowly. On a slope the player can stand high enough above an enemy to be in stomp range.
     */
    private boolean isMetOnTheGround(
        final ActiveLevelObject object,
        final LevelScenePlayer player
    ) {
        final int fallSpeed = object.getVelocity().y();
        final boolean fallingSlowly = fallSpeed >= 0 && fallSpeed < SLOW_FALL_SPEED_LIMIT;
        return player.isOnTheGround() && (object.isGrounded() || !fallingSlowly);
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
            if (!object.isExpired() && object.intersects(tailBounds)) {
                object.onTailAttack(levelScenePlayer);
            }
        }
    }
}
