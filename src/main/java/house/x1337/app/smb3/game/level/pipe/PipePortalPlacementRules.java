package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.game.portal.PipePortal.Cell;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;

/**
 * Where a pipe portal may go. The editor checks these before it places a portal, and the runtime checks them again
 * because the pipe under a portal can be erased after the portal was placed.
 *
 * <p>The ROM decides which pipes can be entered from per-tileset tile ids: the lip tiles {@code TILE1_PIPETB1..4},
 * {@code TILE3_PIPETB5} and {@code TILE1_PIPEH1_B}, gated by {@code PipeTile_EnableByTileset} (dasm prg008.asm:6097).
 * Here a portal marks the pipe instead, and it must cover lip tiles only: every cell over a
 * {@code PIPE_TERMINATION_PART} tile on the static environment layer.
 */
public interface PipePortalPlacementRules {
    String OFF_GRID = "The pipe portal must fit inside the scene.";
    String NOT_OVER_PIPE_TERMINATION = "Every cell of the pipe portal must be over a pipe termination tile.";
    String OVERLAPS_ANOTHER_PORTAL = "The pipe portal overlaps another pipe portal.";

    /**
     * @param dimensions the scene's size
     * @param type       the kind of portal
     * @param row        the anchor's row (the lowest one)
     * @param column     the anchor's column (the leftmost one)
     * @return {@code true} when every cell of the portal is inside the scene
     */
    static boolean fitsInside(
        final LevelSceneDimensions dimensions,
        final VirtualObjectType type,
        final int row,
        final int column
    ) {
        if (dimensions == null || type == null) {
            return false;
        }
        final int topRow = row - type.getRows() + 1;
        final int lastColumn = column + type.getColumns() - 1;
        return topRow >= 0 && row < dimensions.rows() && column >= 0 && lastColumn < dimensions.columns();
    }

    /**
     * @param staticEnvironmentTiles the static environment layer's tiles, indexed {@code [row][column]}
     * @param portal                 the portal to check
     * @return {@code true} when every cell of the portal holds a {@code PIPE_TERMINATION_PART} tile; a cell off the
     *     grid, or holding any other tile, fails
     */
    static boolean isOverPipeTermination(final Tile[][] staticEnvironmentTiles, final PipePortal portal) {
        if (staticEnvironmentTiles == null || portal.getVirtualObjectType() == null) {
            return false;
        }
        for (final Cell cell : portal.cells()) {
            if (!isPipeTermination(staticEnvironmentTiles, cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param existing  the portals already on the scene; the candidate itself (same id) is ignored
     * @param candidate the portal to check
     * @return {@code true} when the candidate shares a cell with any other portal
     */
    static boolean overlapsAny(final List<PipePortal> existing, final PipePortal candidate) {
        if (existing == null) {
            return false;
        }
        for (final PipePortal portal : existing) {
            if (!Objects.equals(portal.getId(), candidate.getId()) && portal.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs every rule in turn.
     *
     * @param dimensions             the scene's size
     * @param staticEnvironmentTiles the static environment layer's tiles
     * @param existing               the portals already on the scene
     * @param candidate              the portal to place
     * @return the first rule broken, as a sentence the editor can show; empty when the placement is allowed
     */
    static Optional<String> validate(
        final LevelSceneDimensions dimensions,
        final Tile[][] staticEnvironmentTiles,
        final List<PipePortal> existing,
        final PipePortal candidate
    ) {
        if (!fitsInside(dimensions, candidate.getVirtualObjectType(), candidate.getRow(), candidate.getColumn())) {
            return Optional.of(OFF_GRID);
        }
        if (!isOverPipeTermination(staticEnvironmentTiles, candidate)) {
            return Optional.of(NOT_OVER_PIPE_TERMINATION);
        }
        if (overlapsAny(existing, candidate)) {
            return Optional.of(OVERLAPS_ANOTHER_PORTAL);
        }
        return Optional.empty();
    }

    private static boolean isPipeTermination(final Tile[][] tiles, final Cell cell) {
        if (cell.row() < 0 || cell.row() >= tiles.length) {
            return false;
        }
        final Tile[] row = tiles[cell.row()];
        if (row == null || cell.column() < 0 || cell.column() >= row.length) {
            return false;
        }
        final Tile tile = row[cell.column()];
        return tile != null && tile.isOfType(PIPE_TERMINATION_PART);
    }
}
