package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.NOT_OVER_PIPE_TERMINATION;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.OFF_GRID;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.OVERLAPS_ANOTHER_PORTAL;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.fitsInside;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.isOverPipeTermination;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.overlapsAny;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.validate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A pipe portal must sit inside the scene, cover pipe lip tiles only, and not overlap another portal.
 */
class PipePortalPlacementRulesTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;
    private static final LevelSceneDimensions DIMENSIONS = new LevelSceneDimensions(COLUMNS, ROWS);

    private Tile[][] staticEnvironment;

    @BeforeEach
    void prepare() {
        staticEnvironment = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : staticEnvironment) {
            Arrays.fill(row, NULL_TILE);
        }
    }

    @Test
    @DisplayName("A portal must fit inside the scene on every edge")
    void fitsInsideTheScene() {
        // Execute & Verify
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_VERTICAL, 0, 2))
            .as("A vertical portal anchored on row 0 pokes above the top")
            .isFalse();
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_VERTICAL, 1, 2)).isTrue();
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_HORIZONTAL, 3, COLUMNS - 1))
            .as("A horizontal portal on the last column pokes past the right edge")
            .isFalse();
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_HORIZONTAL, 3, COLUMNS - 2)).isTrue();
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_HORIZONTAL, ROWS, 2)).as("Below the bottom").isFalse();
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_HORIZONTAL, ROWS - 1, 2)).isTrue();
        assertThat(fitsInside(DIMENSIONS, PIPE_PORTAL_HORIZONTAL, 3, -1)).as("Left of the scene").isFalse();
        assertThat(fitsInside(DIMENSIONS, null, 3, 2)).as("No type, no fit").isFalse();
    }

    @Test
    @DisplayName("A portal is accepted when every cell is over a pipe termination tile")
    void acceptedOverPipeTermination() {
        // Prepare
        paint(3, 2, PIPE_TERMINATION_PART);
        paint(3, 3, PIPE_TERMINATION_PART);
        paint(2, 5, PIPE_TERMINATION_PART);
        paint(3, 5, PIPE_TERMINATION_PART);

        // Execute & Verify
        assertThat(isOverPipeTermination(staticEnvironment, portal(PIPE_PORTAL_HORIZONTAL, 3, 2))).isTrue();
        assertThat(isOverPipeTermination(staticEnvironment, portal(PIPE_PORTAL_VERTICAL, 3, 5))).isTrue();
    }

    @Test
    @DisplayName("A portal is refused when any cell is over pipe body, solid ground or nothing")
    void refusedOverAnythingElse() {
        // Prepare - the anchor is a lip tile each time, the second cell is not
        paint(1, 0, PIPE_TERMINATION_PART);
        paint(1, 1, PIPE_BODY_PART);
        paint(3, 0, PIPE_TERMINATION_PART);
        paint(3, 1, SOLID);
        paint(5, 0, PIPE_TERMINATION_PART);

        // Execute & Verify
        assertThat(isOverPipeTermination(staticEnvironment, portal(PIPE_PORTAL_HORIZONTAL, 1, 0)))
            .as("Pipe body is not a lip")
            .isFalse();
        assertThat(isOverPipeTermination(staticEnvironment, portal(PIPE_PORTAL_HORIZONTAL, 3, 0))).isFalse();
        assertThat(isOverPipeTermination(staticEnvironment, portal(PIPE_PORTAL_HORIZONTAL, 5, 0)))
            .as("NULL_TILE next to the lip")
            .isFalse();
    }

    @Test
    @DisplayName("A portal partly off the grid is never over pipe termination tiles")
    void offGridCellIsNotOverPipeTermination() {
        // Prepare
        paint(0, 4, PIPE_TERMINATION_PART);

        // Execute & Verify
        assertThat(isOverPipeTermination(staticEnvironment, portal(PIPE_PORTAL_VERTICAL, 0, 4))).isFalse();
    }

    @Test
    @DisplayName("Overlap is checked against every other portal but never against the candidate itself")
    void overlapsAnyOtherPortal() {
        // Prepare
        final PipePortal existing = portal(PIPE_PORTAL_HORIZONTAL, 3, 2);
        final PipePortal overlapping = portal(PIPE_PORTAL_VERTICAL, 3, 3);
        final PipePortal apart = portal(PIPE_PORTAL_HORIZONTAL, 3, 4);

        // Execute & Verify
        assertThat(overlapsAny(List.of(existing), overlapping)).isTrue();
        assertThat(overlapsAny(List.of(existing), apart)).isFalse();
        assertThat(overlapsAny(List.of(existing), existing)).as("A portal does not overlap itself").isFalse();
        assertThat(overlapsAny(null, apart)).isFalse();
    }

    @Test
    @DisplayName("Validation reports the first broken rule, or nothing when the placement is allowed")
    void validateReportsTheFirstBrokenRule() {
        // Prepare
        paint(3, 2, PIPE_TERMINATION_PART);
        paint(3, 3, PIPE_TERMINATION_PART);
        paint(3, 4, PIPE_TERMINATION_PART);
        final PipePortal placed = portal(PIPE_PORTAL_HORIZONTAL, 3, 2);
        final List<PipePortal> existing = List.of(placed);

        // Execute & Verify
        assertThat(validate(DIMENSIONS, staticEnvironment, List.of(), portal(PIPE_PORTAL_VERTICAL, 0, 2)))
            .contains(OFF_GRID);
        assertThat(validate(DIMENSIONS, staticEnvironment, List.of(), portal(PIPE_PORTAL_HORIZONTAL, 1, 2)))
            .contains(NOT_OVER_PIPE_TERMINATION);
        assertThat(validate(DIMENSIONS, staticEnvironment, existing, portal(PIPE_PORTAL_HORIZONTAL, 3, 3)))
            .contains(OVERLAPS_ANOTHER_PORTAL);
        assertThat(validate(DIMENSIONS, staticEnvironment, List.of(), placed)).isEmpty();
    }

    private void paint(final int row, final int column, final TileType type) {
        staticEnvironment[row][column] = Tile.builder().id(100 + row * COLUMNS + column).type(type).build();
    }

    private static PipePortal portal(final VirtualObjectType type, final int row, final int column) {
        return PipePortal.builder()
            .type(IN)
            .virtualObjectType(type)
            .row(row)
            .column(column)
            .build();
    }
}
