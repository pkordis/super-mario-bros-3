package house.x1337.app.smb3.game.collision;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ActiveObjectGrid uniform-grid broadphase")
class ActiveObjectGridTest {

    private static final int CELL = 16;

    @Test
    @DisplayName("query returns only objects near the region, not distant ones")
    void queryReturnsNearbyOnly() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
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
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
        final StubObject wide = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions));
        grid.insert(wide);

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(0, 0, dimensions))).containsExactly(wide);
    }

    @Test
    @DisplayName("clear empties every bucket")
    void clearEmptiesGrid() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
        grid.insert(new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions)));
        grid.clear();

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(0, 0, dimensions))).isEmpty();
    }

    @Test
    @DisplayName("objects at negative coordinates bucket and query distinctly")
    void negativeCoordinates() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
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
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
        final StubObject a = new StubObject(AxisAlignedBoundingBox.ofSize(0, 0, dimensions));
        final StubObject b = new StubObject(AxisAlignedBoundingBox.ofSize(8, 8, dimensions));
        grid.insert(a);
        grid.insert(b);

        assertThat(grid.query(AxisAlignedBoundingBox.ofSize(0, 0, new DimensionsPixels(1, 1)))).contains(a, b);
    }

    @Test
    @DisplayName("cellSize must be positive")
    void rejectsNonPositiveCellSize() {
        assertThatThrownBy(() -> new ActiveObjectGrid<>(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActiveObjectGrid<>(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the collision pass strikes an object the tail overlaps, on a strike frame")
    void collisionPassResolvesTheTailStrike() {
        // Prepare — an object beside the player, outside its body box but inside the tail box.
        final DimensionsPixels dimensions = new DimensionsPixels(16, 16);
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
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
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
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
        final ActiveObjectGrid<StubObject> grid = new ActiveObjectGrid<>(CELL);
        final StubObject retired = new StubObject(AxisAlignedBoundingBox.ofSize(20, 0, dimensions));
        grid.insert(retired);
        final LevelScenePlayer player = strikingPlayer(true, AxisAlignedBoundingBox.ofSize(17, 0, dimensions));

        // Execute — the manager pass dropped it, so this tick's grid no longer holds it
        grid.clear();
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(retired.tailAttackCount).isZero();
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
        private int collisionCount;
        private int tailAttackCount;

        private StubObject(final AxisAlignedBoundingBox bounds) {
            this.bounds = bounds;
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
        public ImageResource getImageResource() {
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
