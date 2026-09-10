package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link StaticEnvironmentCollisionGrid#removeLevelObjectAt} against the layer-consolidation trap.
 *
 * <p>The grid is built from layers flattened into one array, so an interactive tile (a brick) overwrites
 * anything sharing its cell (a walkable decoration). Retiring the brick — broken, or collected as a coin
 * during a P-Switch — must expose the decoration again rather than leaving a hole, because the renderer
 * keeps drawing it: layers are separate geometries, so it was never painted over. Before the underlay view
 * existed, the player fell through a panel they could plainly see.
 */
class CollisionGridUnderlayRestoreTest {

    @Test
    @DisplayName("Removing a tile that covers a walkable one exposes it instead of a hole")
    void removalExposesTheCoveredTile() {
        // Prepare
        final LevelObject brick = collidableMock();
        final LevelObject panelTop = collidableMock();
        final LevelObject[][] surface = emptyGrid();
        final LevelObject[][] underlay = emptyGrid();
        surface[3][2] = brick;
        underlay[3][2] = panelTop;

        final StaticEnvironmentCollisionGrid grid = gridFor(surface, underlay);

        // Execute
        grid.removeLevelObjectAt(Offset.of(2, 3));

        // Verify
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3))).isSameAs(panelTop);
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3)).isCollidable())
            .as("The exposed decoration is still standable")
            .isTrue();
    }

    @Test
    @DisplayName("Removing a tile with nothing beneath it leaves the cell empty")
    void removalWithNoUnderlayLeavesTheCellEmpty() {
        // Prepare
        final LevelObject[][] surface = emptyGrid();
        surface[3][2] = collidableMock();

        final StaticEnvironmentCollisionGrid grid = gridFor(surface, emptyGrid());

        // Execute
        grid.removeLevelObjectAt(Offset.of(2, 3));

        // Verify
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3))).isSameAs(EMPTY_LEVEL_OBJECT);
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3)).isCollidable()).isFalse();
    }

    @Test
    @DisplayName("Removing out of bounds is ignored, not fatal")
    void outOfBoundsRemovalIsIgnored() {
        // Prepare
        final StaticEnvironmentCollisionGrid grid = gridFor(emptyGrid(), emptyGrid());

        // Execute
        grid.removeLevelObjectAt(Offset.of(99, 99));

        // Verify
        assertThat(grid.getLevelObjectAt(Offset.of(99, 99))).isSameAs(EMPTY_LEVEL_OBJECT);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelObject collidableMock() {
        final LevelObject object = mock(LevelObject.class);
        when(object.isCollidable()).thenReturn(true);
        return object;
    }

    private LevelObject[][] emptyGrid() {
        final LevelObject[][] objects = new LevelObject[8][8];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private StaticEnvironmentCollisionGrid gridFor(
        final LevelObject[][] surface,
        final LevelObject[][] underlay
    ) {
        final StaticEnvironmentCollisionGrid collisionGrid = new StaticEnvironmentCollisionGrid(
            null,
            new PowerSwitchTimeWindow()
        );
        collisionGrid.setObjects(surface);
        collisionGrid.setUnderlayObjects(underlay);
        collisionGrid.setDimensions(new LevelSceneDimensions(8, 8));
        return collisionGrid;
    }
}
