package house.x1337.app.smb3.model.game.portal;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.portal.PipePortal.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A portal's footprint is worked out from its anchor, which is its lowest leftmost cell: the rows run up from the
 * anchor and the columns run right from it.
 */
class PipePortalCapabilitiesTest {
    private static final int ROW = 5;
    private static final int COLUMN = 3;

    @Test
    @DisplayName("A horizontal portal covers its anchor and the cell to its right")
    void horizontalPortalCells() {
        // Prepare
        final PipePortal portal = portal(PIPE_PORTAL_HORIZONTAL, ROW, COLUMN);

        // Execute & Verify
        assertThat(portal.getRows()).isEqualTo(1);
        assertThat(portal.getColumns()).isEqualTo(2);
        assertThat(portal.cells()).containsExactly(new Cell(ROW, COLUMN), new Cell(ROW, COLUMN + 1));
    }

    @Test
    @DisplayName("A vertical portal covers its anchor and the cell above it")
    void verticalPortalCells() {
        // Prepare
        final PipePortal portal = portal(PIPE_PORTAL_VERTICAL, ROW, COLUMN);

        // Execute & Verify
        assertThat(portal.getRows()).isEqualTo(2);
        assertThat(portal.getColumns()).isEqualTo(1);
        assertThat(portal.getTopRow()).isEqualTo(ROW - 1);
        assertThat(portal.cells()).containsExactly(new Cell(ROW - 1, COLUMN), new Cell(ROW, COLUMN));
    }

    @Test
    @DisplayName("A portal occupies the cells of its footprint and nothing around them")
    void occupiesOnlyItsFootprint() {
        // Prepare
        final PipePortal horizontal = portal(PIPE_PORTAL_HORIZONTAL, ROW, COLUMN);
        final PipePortal vertical = portal(PIPE_PORTAL_VERTICAL, ROW, COLUMN);

        // Execute & Verify
        assertThat(horizontal.occupies(ROW, COLUMN + 1)).isTrue();
        assertThat(horizontal.occupies(ROW, COLUMN + 2)).as("Past the right edge").isFalse();
        assertThat(horizontal.occupies(ROW - 1, COLUMN)).as("Above a one-row portal").isFalse();
        assertThat(horizontal.occupies(ROW, COLUMN - 1)).as("Left of the anchor").isFalse();
        assertThat(vertical.occupies(ROW - 1, COLUMN)).isTrue();
        assertThat(vertical.occupies(ROW + 1, COLUMN)).as("Below the anchor").isFalse();
        assertThat(vertical.occupies(ROW - 2, COLUMN)).as("Above the top row").isFalse();
        assertThat(vertical.occupies(ROW, COLUMN + 1)).as("Right of a one-column portal").isFalse();
    }

    @Test
    @DisplayName("Two portals overlap only when they share a cell")
    void overlapsWhenSharingACell() {
        // Prepare
        final PipePortal horizontal = portal(PIPE_PORTAL_HORIZONTAL, ROW, COLUMN);
        final PipePortal verticalOnItsRightCell = portal(PIPE_PORTAL_VERTICAL, ROW, COLUMN + 1);
        final PipePortal horizontalAlongside = portal(PIPE_PORTAL_HORIZONTAL, ROW, COLUMN + 2);
        final PipePortal verticalBelow = portal(PIPE_PORTAL_VERTICAL, ROW + 2, COLUMN);

        // Execute & Verify
        assertThat(horizontal.overlaps(verticalOnItsRightCell)).isTrue();
        assertThat(verticalOnItsRightCell.overlaps(horizontal)).as("Overlap is symmetric").isTrue();
        assertThat(horizontal.overlaps(horizontalAlongside)).as("Touching edges do not overlap").isFalse();
        assertThat(horizontal.overlaps(verticalBelow)).isFalse();
    }

    @Test
    @DisplayName("A portal without a type covers no cells")
    void portalWithoutATypeHasNoCells() {
        // Prepare
        final PipePortal portal = portal(null, ROW, COLUMN);

        // Execute & Verify
        assertThat(portal.cells()).isEmpty();
        assertThat(portal.occupies(ROW, COLUMN)).isFalse();
    }

    @Test
    @DisplayName("Every built portal gets its own id")
    void freshIdPerPortal() {
        // Execute
        final PipePortal first = portal(PIPE_PORTAL_HORIZONTAL, ROW, COLUMN);
        final PipePortal second = portal(PIPE_PORTAL_HORIZONTAL, ROW, COLUMN);

        // Verify
        assertThat(first.getId()).isNotBlank();
        assertThat(second.getId()).isNotBlank().isNotEqualTo(first.getId());
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
