package house.x1337.app.smb3.game.collision;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.EnemySpawner;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ActiveObjectGrid uniform-grid broadphase")
class ActiveObjectGridTest {

    @Test
    @DisplayName("query returns only objects near the region, not distant ones")
    void queryReturnsNearbyOnly() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject near = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions));
        final StubObject faraway = new StubObject(AxisAlignedBoundingBox.ofSize(1000, 1000, dimensions));
        grid.insert(near);
        grid.insert(faraway);

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(8, 8, dimensions))).containsExactly(near);
    }

    @Test
    @DisplayName("an object spanning several cells is returned once")
    void multiCellObjectDeduped() {
        final DimensionsPixels dimensions = new DimensionsPixels(64, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject wide = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions));
        grid.insert(wide);

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(0, 0, dimensions))).containsExactly(wide);
    }

    @Test
    @DisplayName("clear empties every bucket")
    void clearEmptiesGrid() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        grid.insert(new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)));
        grid.clear();

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(0, 0, dimensions))).isEmpty();
    }

    @Test
    @DisplayName("objects at negative coordinates bucket and query distinctly")
    void negativeCoordinates() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject object = new StubObject(AxisAlignedBoundingBox.ofSize(-40, -40, dimensions));
        grid.insert(object);

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(-40, -40, dimensions))).containsExactly(object);
        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(100, 100, dimensions))).isEmpty();
    }

    @Test
    @DisplayName("two non-overlapping objects in the same cell both surface as candidates")
    void sharedCellSurfacesBothCandidates() {
        // The grid is a broadphase: cell-sharers are returned and the caller narrowphases.
        final DimensionsPixels dimensions = new DimensionsPixels(4, 4);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject a = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions));
        final StubObject b = new StubObject(AxisAlignedBoundingBox.ofSize(8, 8, dimensions));
        grid.insert(a);
        grid.insert(b);

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(0, 0, new DimensionsPixels(1, 1)))).contains(a, b);
    }

    @Test
    @DisplayName("the collision pass strikes an object the tail overlaps, on a strike frame")
    void collisionPassResolvesTheTailStrike() {
        // Prepare — an object beside the player, outside its body box but inside the tail box.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = new StubObject(AxisAlignedBoundingBox.ofSize(20, 0, dimensions));
        grid.insert(enemy);
        final LevelScenePlayer player = strikingPlayer(true, AxisAlignedBoundingBox.ofSize(17, 0, dimensions));

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify — struck by the tail, untouched by the body
        assertThat(enemy.tailAttackCount).as("tail strikes dispatched").isEqualTo(1);
        assertThat(enemy.collisionCount).as("body collisions dispatched").isZero();
    }

    @Test
    @DisplayName("no strike is dispatched on a non-strike frame, even while the tail overlaps")
    void noStrikeOutsideTheStrikeFrames() {
        // Prepare — same overlap, but the player reports it is not on a kick frame ($0C / $09).
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = new StubObject(AxisAlignedBoundingBox.ofSize(20, 0, dimensions));
        grid.insert(enemy);
        final LevelScenePlayer player = strikingPlayer(false, AxisAlignedBoundingBox.ofSize(17, 0, dimensions));

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify — the hitbox is never even asked for
        assertThat(enemy.tailAttackCount).isZero();
        verify(player, never()).getTailAttackBounds();
    }

    @Test
    @DisplayName("only objects inserted this tick can be struck — a cleared grid strikes nothing")
    void aClearedGridStrikesNothing() {
        // Pins the timing guarantee the pass exists to provide: the tail resolves against the grid as
        // the motion managers left it this tick (dasm prg000 @ PRG000_C9B6 — each object is tested
        // right after it moves), so an object the managers retired is unreachable.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject retired = new StubObject(AxisAlignedBoundingBox.ofSize(20, 0, dimensions));
        grid.insert(retired);
        final LevelScenePlayer player = strikingPlayer(true, AxisAlignedBoundingBox.ofSize(17, 0, dimensions));

        // Execute — the manager pass dropped it, so this tick's grid no longer holds it
        grid.clear();
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(retired.tailAttackCount).isZero();
    }

    @Test
    @DisplayName("a descending player landing on an opt-in object's top triggers onCollisionFromAbove")
    void stompTriggersOnCollisionFromAbove() {
        // Prepare — enemy occupying [20,36]x[80,96]; the player's feet have just broken its top edge.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = enemyAt(20, 80);
        grid.insert(enemy);
        final LevelScenePlayer player = playerAt(20, 49, 1.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify — the stomp branch, and only it
        assertThat(enemy.fromAboveCount).as("onCollisionFromAbove").isEqualTo(1);
        assertThat(enemy.fromBelowCount).isZero();
        assertThat(enemy.overlapCount).isZero();
        assertThat(enemy.collisionCount).as("undirected onCollisionWith still fires").isEqualTo(1);
    }

    @Test
    @DisplayName("a stomp needs no horizontal centring — clipping the enemy's edge counts")
    void stompDoesNotRequireHorizontalCentring() {
        // Prepare — the player's hitbox is [10,22], overlapping the enemy's [20,36] by just 2px. The ROM
        // never looks at the horizontal axis to decide a stomp (Player_HitEnemy @ PRG000_D218); an
        // earlier least-overlapping-axis rule here demanded roughly half the player's width on the
        // enemy, which is what made stomps feel like they needed dead-centre aim.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = enemyAt(20, 80);
        grid.insert(enemy);
        final LevelScenePlayer player = playerAt(8, 49, 1.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(enemy.fromAboveCount).as("a 2px clip still stomps").isEqualTo(1);
        assertThat(enemy.overlapCount).isZero();
    }

    @Test
    @DisplayName("landing across two adjacent objects stomps both in the same tick")
    void landingBetweenTwoObjectsStompsBoth() {
        // Prepare — two enemies flush side by side, [20,36] and [36,52]; the player straddles the seam
        // with 6px on each. Each object is hit-tested independently, exactly as the ROM's object loop
        // reaches Player_HitEnemy once per object, so both are pressed on the same frame.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject leftEnemy = enemyAt(20, 80);
        final StubObject rightEnemy = enemyAt(36, 80);
        grid.insert(leftEnemy);
        grid.insert(rightEnemy);
        final LevelScenePlayer player = playerAt(28, 49, 1.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(leftEnemy.fromAboveCount).as("left enemy stomped").isEqualTo(1);
        assertThat(rightEnemy.fromAboveCount).as("right enemy stomped").isEqualTo(1);
    }

    @Test
    @DisplayName("a stomp registers on the very first tick of contact, at any fall speed")
    void stompRegistersOnFirstContactTick() {
        // Prepare — a fast fall breaks the enemy's top edge by 4px on the tick it is first detected.
        // There is no minimum sink depth in the ROM, and a depth requirement scaled badly with speed:
        // the faster the fall, the more horizontal overlap the old axis rule demanded alongside it.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = enemyAt(20, 80);
        grid.insert(enemy);
        final LevelScenePlayer player = playerAt(20, 52, 4.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(enemy.fromAboveCount).isEqualTo(1);
    }

    @Test
    @DisplayName("running into an object's side is a body hit, not a stomp")
    void sideHitIsNotAStomp() {
        // Prepare — the player's feet are level with the enemy's, so its reference Y (96 - 32 = 64) is
        // only 16px above the enemy's top: inside the 19px stomp range, hence a body hit. This is the
        // case the old least-overlapping-axis test existed to catch; the vertical threshold covers it.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = enemyAt(20, 80);
        grid.insert(enemy);
        final LevelScenePlayer player = playerAt(30, 64, 1.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(enemy.overlapCount).as("onPlayerOverlap — the ROM hurts the player here").isEqualTo(1);
        assertThat(enemy.fromAboveCount).isZero();
        assertThat(enemy.fromBelowCount).isZero();
    }

    @Test
    @DisplayName("a rising player too deep to be stomping triggers onCollisionFromBelow")
    void risingPlayerTriggersOnCollisionFromBelow() {
        // Prepare — same depth as the side hit, but rising into the object.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = enemyAt(20, 80);
        grid.insert(enemy);
        final LevelScenePlayer player = playerAt(20, 64, -1.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(enemy.fromBelowCount).as("onCollisionFromBelow").isEqualTo(1);
        assertThat(enemy.fromAboveCount).isZero();
        assertThat(enemy.overlapCount).isZero();
    }

    @Test
    @DisplayName("a player rising while still clear of the object dispatches nothing directional")
    void risingWhileAboveDispatchesNothing() {
        // Prepare — within stomp range but moving up: the bounce a stomp just granted. Re-dispatching
        // onCollisionFromAbove here would stomp the same enemy twice.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject enemy = enemyAt(20, 80);
        grid.insert(enemy);
        final LevelScenePlayer player = playerAt(20, 49, -4.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(enemy.fromAboveCount).isZero();
        assertThat(enemy.fromBelowCount).isZero();
        assertThat(enemy.overlapCount).isZero();
        assertThat(enemy.collisionCount).as("the undirected contact still fires").isEqualTo(1);
    }

    @Test
    @DisplayName("an object that does not opt in gets only the undirected onCollisionWith")
    void nonOptInObjectGetsOnlyUndirectedCollision() {
        // Prepare — a reward-like object (directional == false) landed on from above.
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject reward = new StubObject(
            AxisAlignedBoundingBox.ofSize(20, 80, new DimensionsPixels(16, 16)),
            false
        );
        grid.insert(reward);
        final LevelScenePlayer player = playerAt(20, 49, 1.0);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify — no directional method is dispatched to it
        assertThat(reward.collisionCount).isEqualTo(1);
        assertThat(reward.fromAboveCount).isZero();
        assertThat(reward.fromBelowCount).isZero();
        assertThat(reward.overlapCount).isZero();
    }

    @Test
    @DisplayName("two bouncing objects overlapping side-on are each told to turn from the other")
    void sideOverlapBumpsBothBouncers() {
        // Prepare — boxes [0,16] and [10,26] on the same row: 6px of X overlap against a full 16px of Y,
        // so X is the shallower axis and they met left-to-right.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject left = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)).bouncing();
        final StubObject right = new StubObject(AxisAlignedBoundingBox.ofSize(10, 0, dimensions)).bouncing();
        grid.insert(left);
        grid.insert(right);

        // Execute
        grid.resolveObjectToObjectBumps();

        // Verify — symmetric: each is handed the other, so both can turn away
        assertThat(left.bumpedFrom).containsExactly(right);
        assertThat(right.bumpedFrom).containsExactly(left);
    }

    @Test
    @DisplayName("a bouncing object landing on another's head is not a side bump")
    void verticalOverlapDoesNotBump() {
        // Prepare — same column, 6px of Y overlap against a full 16px of X: Y is the shallower axis, so
        // they met top-to-bottom. Only left/right contact turns a walker around.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject upper = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)).bouncing();
        final StubObject lower = new StubObject(AxisAlignedBoundingBox.ofSize(0, 10, dimensions)).bouncing();
        grid.insert(upper);
        grid.insert(lower);

        // Execute
        grid.resolveObjectToObjectBumps();

        // Verify
        assertThat(upper.bumpedFrom).isEmpty();
        assertThat(lower.bumpedFrom).isEmpty();
    }

    @Test
    @DisplayName("a bouncing object is unaffected by one that does not bounce")
    void nonBouncingNeighbourIsIgnored() {
        // Prepare — a walker overlapping a reward-like object side-on. The ROM checks the attribute flag
        // before turning anything, so a coin must not steer an enemy.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject walker = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)).bouncing();
        final StubObject reward = new StubObject(AxisAlignedBoundingBox.ofSize(10, 0, dimensions));
        grid.insert(walker);
        grid.insert(reward);

        // Execute
        grid.resolveObjectToObjectBumps();

        // Verify
        assertThat(walker.bumpedFrom).as("the reward does not steer the walker").isEmpty();
        assertThat(reward.bumpedFrom).as("and is never dispatched to itself").isEmpty();
    }

    @Test
    @DisplayName("bouncing objects that merely share an edge, or stand apart, do not bump")
    void touchingOrSeparatedBouncersDoNotBump() {
        // Prepare — [0,16] and [16,32] share exactly one edge, which the half-open convention excludes.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject flush = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)).bouncing();
        final StubObject adjacent = new StubObject(AxisAlignedBoundingBox.ofSize(16, 0, dimensions)).bouncing();
        final StubObject distant = new StubObject(AxisAlignedBoundingBox.ofSize(400, 0, dimensions)).bouncing();
        grid.insert(flush);
        grid.insert(adjacent);
        grid.insert(distant);

        // Execute
        grid.resolveObjectToObjectBumps();

        // Verify
        assertThat(flush.bumpedFrom).isEmpty();
        assertThat(adjacent.bumpedFrom).isEmpty();
        assertThat(distant.bumpedFrom).isEmpty();
    }

    @Test
    @DisplayName("a lone bouncing object is never bumped by itself")
    void aBouncerNeverBumpsItself() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject lonely = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)).bouncing();
        grid.insert(lonely);

        grid.resolveObjectToObjectBumps();

        assertThat(lonely.bumpedFrom).isEmpty();
    }

    @Test
    @DisplayName("a crowd of three in a row bumps each neighbour it actually overlaps")
    void middleBouncerSeesBothNeighbours() {
        // Prepare — [0,16], [10,26], [20,36]: the middle overlaps both ends, the ends only the middle.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = newGrid();
        final StubObject first = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)).bouncing();
        final StubObject middle = new StubObject(AxisAlignedBoundingBox.ofSize(10, 0, dimensions)).bouncing();
        final StubObject last = new StubObject(AxisAlignedBoundingBox.ofSize(20, 0, dimensions)).bouncing();
        grid.insert(first);
        grid.insert(middle);
        grid.insert(last);

        // Execute
        grid.resolveObjectToObjectBumps();

        // Verify
        assertThat(middle.bumpedFrom).containsExactlyInAnyOrder(first, last);
        assertThat(first.bumpedFrom).containsExactly(middle);
        assertThat(last.bumpedFrom).containsExactly(middle);
    }

    /**
     * A grid under test. The cell size is the tile constant now, so the only collaborator is the enemy
     * spawner, which nothing here exercises (it is only reached via {@code spawnPlacedEnemies}).
     */
    private static ActiveObjectGrid<StubObject> newGrid() {
        return new ActiveObjectGrid<>(mock(EnemySpawner.class));
    }

    /**
     * A player double placed as the real one is: {@code positionY} is the reference Y the stomp test
     * measures from, and the hitbox is derived from it exactly as {@code getObjectCollisionBounds()} does
     * for a large standing player — X spans {@code +2..+14}, Y spans {@code +6..+32}. Keeping the two in
     * step is what makes these tests mean anything; a box detached from the reference Y could satisfy the
     * threshold at a geometry the player can never actually reach.
     *
     * @param positionX the player's position X in sprite-pixel space
     * @param positionY the player's position Y — its reference top, with the feet at {@code +32}
     * @param dy        vertical velocity; negative is rising
     */
    private static LevelScenePlayer playerAt(final double positionX, final double positionY, final double dy) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(positionX);
        position.setY(positionY);
        position.setDY(dy);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.isTailAttackStriking()).thenReturn(false);
        when(player.getObjectCollisionBounds()).thenReturn(new AxisAlignedBoundingBox(
            positionX + 2,
            positionY + 6,
            positionX + 14,
            positionY + 32
        ));
        return player;
    }

    /** A 16x16 opt-in (directional) object at the given top-left, as a Goomba's box is. */
    private static StubObject enemyAt(final double left, final double top) {
        return new StubObject(
            AxisAlignedBoundingBox.ofSize(left, top, new DimensionsPixels(16, 16)),
            true
        );
    }

    /**
     * A player double that reports the given swing state and tail hitbox, and a body box far from
     * everything so only the tail can register.
     */
    private static LevelScenePlayer strikingPlayer(final boolean striking, final AxisAlignedBoundingBox tail) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getObjectCollisionBounds())
            .thenReturn(AxisAlignedBoundingBox.ofSize(-1000, -1000, new DimensionsPixels(16, 16)));
        when(player.isTailAttackStriking()).thenReturn(striking);
        when(player.getTailAttackBounds()).thenReturn(tail);
        return player;
    }

    /** Minimal {@link ActiveLevelObject} double — only {@code getBounds()} and identity matter here. */
    private static final class StubObject implements ActiveLevelObject {
        private final AxisAlignedBoundingBox bounds;
        private final boolean directional;
        private final List<ActiveLevelObject> bumpedFrom = new ArrayList<>();
        private boolean bouncing;
        private int collisionCount;
        private int tailAttackCount;
        private int fromAboveCount;
        private int fromBelowCount;
        private int overlapCount;

        private StubObject(final AxisAlignedBoundingBox bounds) {
            this(bounds, false);
        }

        private StubObject(final AxisAlignedBoundingBox bounds, final boolean directional) {
            this.bounds = bounds;
            this.directional = directional;
        }

        /** Opts this stub into object-to-object bumping, as a Goomba does. */
        private StubObject bouncing() {
            bouncing = true;
            return this;
        }

        @Override
        public boolean bouncesOffOtherObjects() {
            return bouncing;
        }

        @Override
        public void onSideCollisionWith(final ActiveLevelObject other) {
            bumpedFrom.add(other);
        }

        @Override
        public double getPixelX() {
            throw doNotCall();
        }

        @Override
        public double getPixelY() {
            throw doNotCall();
        }

        @Override
        public Dimensions getSpriteDimensions() {
            throw doNotCall();
        }

        private RuntimeException doNotCall() {
            return new UnsupportedOperationException("This method should not be called");
        }

        @Override
        public AxisAlignedBoundingBox getBounds() {
            return bounds;
        }

        @Override
        public void onCollisionWith(final LevelScenePlayer player) {
            collisionCount++;
        }

        @Override
        public boolean resolvesDirectionalPlayerCollision() {
            return directional;
        }

        @Override
        public void onCollisionFromAbove(final LevelScenePlayer player) {
            fromAboveCount++;
        }

        @Override
        public void onCollisionFromBelow(final LevelScenePlayer player) {
            fromBelowCount++;
        }

        @Override
        public void onPlayerOverlap(final LevelScenePlayer player) {
            overlapCount++;
        }

        @Override
        public void onTailAttack(final LevelScenePlayer player) {
            tailAttackCount++;
        }

        @Override
        public Geometry getSpriteGeometry() {
            return null;
        }

        @Override
        public Offset getOffset() {
            return null;
        }

        @Override
        public LevelObjectType getType() {
            return null;
        }

        @Override
        public GameEngine getGameEngine() {
            return null;
        }
    }
}
