package house.x1337.app.smb3.model.game.portal;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.portal.PipePortal.Cell;

import java.util.ArrayList;
import java.util.List;

public sealed interface PipePortalCapabilities permits PipePortal {
    default int getRows() {
        final VirtualObjectType type = ((PipePortal) this).getVirtualObjectType();
        return type == null ? 0 : type.getRows();
    }

    default int getColumns() {
        final VirtualObjectType type = ((PipePortal) this).getVirtualObjectType();
        return type == null ? 0 : type.getColumns();
    }

    default int getTopRow() {
        return ((PipePortal) this).getRow() - getRows() + 1;
    }

    default List<Cell> cells() {
        final PipePortal portal = (PipePortal) this;
        final List<Cell> cells = new ArrayList<>(getRows() * getColumns());
        for (int row = getTopRow(); row <= portal.getRow(); row++) {
            for (int column = portal.getColumn(); column < portal.getColumn() + getColumns(); column++) {
                cells.add(new Cell(row, column));
            }
        }
        return cells;
    }

    default boolean occupies(final int row, final int column) {
        final PipePortal portal = (PipePortal) this;
        return row >= getTopRow()
            && row <= portal.getRow()
            && column >= portal.getColumn()
            && column < portal.getColumn() + getColumns();
    }

    default boolean overlaps(final PipePortal other) {
        for (final Cell cell : other.cells()) {
            if (occupies(cell.row(), cell.column())) {
                return true;
            }
        }
        return false;
    }

    default boolean isLinked() {
        return ((PipePortal) this).getLinksToId() != null;
    }
}
