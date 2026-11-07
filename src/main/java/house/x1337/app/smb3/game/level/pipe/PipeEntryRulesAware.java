package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.Optional;

import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.LEFT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;

public interface PipeEntryRulesAware {
    int[] ALIGNMENT_OFFSETS = {8, 4, 4};
    int ALIGNMENT_OFFSET_AIRBORNE = 0;
    int ALIGNMENT_OFFSET_NOT_SMALL = 1;
    int ALIGNMENT_OFFSET_SMALL = 2;

    default boolean isAligned(
        final int playerX,
        final boolean airborne,
        final boolean small,
        final boolean leftCell
    ) {
        final int offsetIndex;
        if (airborne) {
            offsetIndex = ALIGNMENT_OFFSET_AIRBORNE;
        } else if (small) {
            offsetIndex = ALIGNMENT_OFFSET_SMALL;
        } else {
            offsetIndex = ALIGNMENT_OFFSET_NOT_SMALL;
        }
        int relative = playerX & 15;
        if (((relative + ALIGNMENT_OFFSETS[offsetIndex]) & 16) == 0) {
            relative |= 0xF0;
        }
        final int lipHalf = leftCell ? 16 : 0;
        return ((relative + lipHalf - 3) & 255) < 10;
    }

    default PipeEntryDirection sidePipeDirection(final int playerX) {
        return (playerX & 15) < 8 ? RIGHT : LEFT;
    }

    default boolean isEnterable(final PipePortal portal) {
        return portal.getType() != null && portal.getType().isEnterable();
    }

    default boolean isEntryCell(
        final PipePortal portal,
        final int row,
        final int column
    ) {
        if (portal.getVirtualObjectType() == PIPE_PORTAL_VERTICAL) {
            return row == portal.getRow() && column == portal.getColumn();
        }
        return portal.occupies(row, column);
    }

    default Optional<PipeEntryDirection> entryDirection(
        final Tile[][] staticEnvironmentTiles,
        final PipePortal portal,
        final int playerX
    ) {
        if (!PipePortalPlacementRules.isOverPipeTermination(staticEnvironmentTiles, portal)) {
            return Optional.empty();
        }
        if (portal.getVirtualObjectType() == PIPE_PORTAL_VERTICAL) {
            return Optional.of(sidePipeDirection(playerX));
        }
        if (isPipeBodyRow(staticEnvironmentTiles, portal, portal.getRow() + 1)) {
            return Optional.of(DOWN);
        }
        if (isPipeBodyRow(staticEnvironmentTiles, portal, portal.getRow() - 1)) {
            return Optional.of(UP);
        }
        return Optional.empty();
    }

    default Optional<PipeEntryDirection> exitDirection(
        final Tile[][] staticEnvironmentTiles,
        final PipePortal portal
    ) {
        if (!PipePortalPlacementRules.isOverPipeTermination(staticEnvironmentTiles, portal)) {
            return Optional.empty();
        }
        if (portal.getVirtualObjectType() == PIPE_PORTAL_VERTICAL) {
            if (isPipeBodyColumn(staticEnvironmentTiles, portal, portal.getColumn() + 1)) {
                return Optional.of(LEFT);
            }
            if (isPipeBodyColumn(staticEnvironmentTiles, portal, portal.getColumn() - 1)) {
                return Optional.of(RIGHT);
            }
            return Optional.empty();
        }
        if (isPipeBodyRow(staticEnvironmentTiles, portal, portal.getRow() + 1)) {
            return Optional.of(UP);
        }
        if (isPipeBodyRow(staticEnvironmentTiles, portal, portal.getRow() - 1)) {
            return Optional.of(DOWN);
        }
        return Optional.empty();
    }

    private static boolean isPipeBodyColumn(final Tile[][] tiles, final PipePortal portal, final int column) {
        for (int row = portal.getTopRow(); row <= portal.getRow(); row++) {
            if (!isPipeBody(tiles, row, column)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPipeBody(final Tile[][] tiles, final int row, final int column) {
        if (row < 0 || row >= tiles.length || tiles[row] == null || column < 0 || column >= tiles[row].length) {
            return false;
        }
        final Tile tile = tiles[row][column];
        return tile != null && tile.isOfType(PIPE_BODY_PART);
    }

    private static boolean isPipeBodyRow(final Tile[][] tiles, final PipePortal portal, final int row) {
        for (int column = portal.getColumn(); column < portal.getColumn() + portal.getColumns(); column++) {
            if (!isPipeBody(tiles, row, column)) {
                return false;
            }
        }
        return true;
    }
}
