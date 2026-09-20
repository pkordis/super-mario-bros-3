package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @DisplayName("A tile whose fallback is itself is retired outright, not re-exposed")
    void removalOfATileStackedOverItselfEmptiesTheCell() {
        // Prepare - the shape the reported bug took: a brick reachable from both views, so retiring the
        // surface copy handed back an identical brick and the player could smash it forever.
        final LevelObject brick = collidableMock();
        final LevelObject[][] surface = emptyGrid();
        final LevelObject[][] underlay = emptyGrid();
        surface[3][2] = brick;
        underlay[3][2] = brick;

        final StaticEnvironmentCollisionGrid grid = gridFor(surface, underlay);

        // Execute - broken once, then whatever is exposed is broken again
        grid.removeLevelObjectAt(Offset.of(2, 3));
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3))).isSameAs(brick);
        grid.removeLevelObjectAt(Offset.of(2, 3));

        // Verify - the cell is gone for good rather than restoring the same stand-in indefinitely
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3))).isSameAs(EMPTY_LEVEL_OBJECT);
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3)).isCollidable()).isFalse();
    }

    @Test
    @DisplayName("The exposed tile is only offered once: a second removal clears the cell")
    void fallbackIsConsumedWhenPromoted() {
        // Prepare
        final LevelObject panelTop = collidableMock();
        final LevelObject[][] surface = emptyGrid();
        final LevelObject[][] underlay = emptyGrid();
        surface[3][2] = collidableMock();
        underlay[3][2] = panelTop;

        final StaticEnvironmentCollisionGrid grid = gridFor(surface, underlay);

        // Execute
        grid.removeLevelObjectAt(Offset.of(2, 3));
        grid.removeLevelObjectAt(Offset.of(2, 3));

        // Verify
        assertThat(grid.getLevelObjectAt(Offset.of(2, 3))).isSameAs(EMPTY_LEVEL_OBJECT);
    }

    @Test
    @DisplayName("Layer provenance follows the object, so the right baked texture is erased")
    void sourceLayerFollowsThePromotedObject() {
        // Prepare - a brick in the interactive layer sitting over terrain
        final LevelObject[][] surface = emptyGrid();
        final LevelObject[][] underlay = emptyGrid();
        surface[3][2] = collidableMock();
        underlay[3][2] = collidableMock();

        final StaticEnvironmentCollisionGrid grid = gridFor(surface, underlay);
        grid.setSurfaceLayers(layerGrid(INTERACTIVE_OBJECTS));
        grid.setUnderlayLayers(layerGrid(STATIC_ENVIRONMENT));

        // Verify - the surface tile is painted by the interactive layer
        assertThat(grid.getSourceLayerAt(Offset.of(2, 3))).isEqualTo(INTERACTIVE_OBJECTS);

        // Execute
        grid.removeLevelObjectAt(Offset.of(2, 3));

        // Verify - now the terrain layer paints what shows, so that is what a second break must erase
        assertThat(grid.getSourceLayerAt(Offset.of(2, 3))).isEqualTo(STATIC_ENVIRONMENT);
    }

    @Test
    @DisplayName("Without layer information the interactive layer is assumed, as it was before")
    void sourceLayerDefaultsToTheInteractiveLayer() {
        // Prepare - no provenance set, which is how every pre-existing caller builds a grid
        final StaticEnvironmentCollisionGrid grid = gridFor(emptyGrid(), emptyGrid());

        // Verify
        assertThat(grid.getSourceLayerAt(Offset.of(2, 3))).isEqualTo(INTERACTIVE_OBJECTS);
        assertThat(grid.getSourceLayerAt(Offset.of(99, 99))).isEqualTo(INTERACTIVE_OBJECTS);
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

    private LevelSceneLayerType[][] layerGrid(final LevelSceneLayerType type) {
        final LevelSceneLayerType[][] layers = new LevelSceneLayerType[8][8];
        for (final LevelSceneLayerType[] row : layers) {
            Arrays.fill(row, type);
        }
        return layers;
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
