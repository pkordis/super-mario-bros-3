package house.x1337.app.smb3.model.game.portal;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.Coordinates;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.Coordinates.of;

/**
 * A portal is anchored at its bottom-left cell: {@link PipePortal#getCoordinates()} holds its left column
 * ({@code x}) and its bottom row ({@code y}).
 */
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
        return ((PipePortal) this).getCoordinates().y() - getRows() + 1;
    }

    default List<Coordinates> cells() {
        final Coordinates anchor = ((PipePortal) this).getCoordinates();
        final List<Coordinates> cells = new ArrayList<>(getRows() * getColumns());
        for (int row = getTopRow(); row <= anchor.y(); row++) {
            for (int column = anchor.x(); column < anchor.x() + getColumns(); column++) {
                cells.add(of(column, row));
            }
        }
        return cells;
    }

    default boolean occupies(final Coordinates cell) {
        final Coordinates anchor = ((PipePortal) this).getCoordinates();
        return cell.y() >= getTopRow()
            && cell.y() <= anchor.y()
            && cell.x() >= anchor.x()
            && cell.x() < anchor.x() + getColumns();
    }

    default boolean overlaps(final PipePortal other) {
        for (final Coordinates cell : other.cells()) {
            if (occupies(cell)) {
                return true;
            }
        }
        return false;
    }

    default boolean isLinked() {
        return ((PipePortal) this).getLinksToId() != null;
    }
}
