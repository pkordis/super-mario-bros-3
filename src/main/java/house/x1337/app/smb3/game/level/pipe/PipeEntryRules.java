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

/**
 * The rules from dasm {@code Player_DoSpecialTiles} (prg008.asm:6133-6330) that decide whether the player can go into
 * the pipe under a {@link PipePortal}, and in which direction. The ROM recognises enterable pipes by tile id
 * ({@code TILE1_PIPETB1..4}, {@code TILE1_PIPEH1_B}, gated by {@code PipeTile_EnableByTileset}, prg008.asm:6097);
 * here an {@code IN} or {@code IN_AND_OUT} portal over the pipe's lip takes their place. Position arithmetic is the
 * ROM's 8-bit arithmetic, ported as-is.
 */
public interface PipeEntryRules {
    /**
     * {@code PRG008_BC43} (prg008.asm:6114): the offset added to {@code Player_X & 15}, indexed by
     * {@link #ALIGNMENT_OFFSET_AIRBORNE}, {@link #ALIGNMENT_OFFSET_NOT_SMALL} or {@link #ALIGNMENT_OFFSET_SMALL}.
     */
    int[] ALIGNMENT_OFFSETS = {0x08, 0x04, 0x04};
    int ALIGNMENT_OFFSET_AIRBORNE = 0;
    int ALIGNMENT_OFFSET_NOT_SMALL = 1;
    int ALIGNMENT_OFFSET_SMALL = 2;

    /**
     * Whether the player is close enough to the middle of an upright or upside-down pipe to go in (prg008.asm
     * {@code PRG008_BD1F}-{@code BD4B}). The window is ten pixels wide: with the pipe's left cell starting at
     * column {@code c}, it lets {@code Player_X} through from {@code 16c + 3} to {@code 16c + 12}.
     *
     * <p>{@code lipHalf} is the ROM's {@code Temp_Var2}. The ROM takes {@code TILE1_PIPETB2_R - tile}, which is odd
     * for every left lip tile ({@code TILE1_PIPETB1_L = $AD}, {@code TILE1_PIPETB2_L = $AF}), so the probed cell
     * adds 16 when it is the portal's <b>left</b> cell and 0 when it is the right one.
     *
     * <p>The ROM also picks the airborne offset on a level with slopes ({@code Level_SlopeEn}). The port has no
     * sloped levels, so only being in the air selects it.
     *
     * @param playerX  the player's whole-pixel X
     * @param airborne whether the player is in the air (entering upward)
     * @param small    whether the player is small ({@code Player_Suit == 0})
     * @param leftCell whether the probed cell is the portal's left cell
     * @return {@code true} when the player is inside the window
     */
    static boolean isAligned(final int playerX, final boolean airborne, final boolean small, final boolean leftCell) {
        final int offsetIndex;
        if (airborne) {
            offsetIndex = ALIGNMENT_OFFSET_AIRBORNE;
        } else if (small) {
            offsetIndex = ALIGNMENT_OFFSET_SMALL;
        } else {
            offsetIndex = ALIGNMENT_OFFSET_NOT_SMALL;
        }
        int relative = playerX & 0x0F;
        // AND #$10 on the sum: when it is clear the player is short of the next tile, so the relative X is made
        // "negative, sort of" (ORA #$F0)
        if (((relative + ALIGNMENT_OFFSETS[offsetIndex]) & 0x10) == 0) {
            relative |= 0xF0;
        }
        final int lipHalf = leftCell ? 0x10 : 0x00;
        return ((relative + lipHalf - 3) & 0xFF) < 10;
    }

    /**
     * The direction to hold to enter a side pipe ({@code Pipe_PadDirForEnter}, prg008.asm:6175-6186): {@code RIGHT}
     * on the left half of a tile, {@code LEFT} on the right half. It matches the side the collision grid probes in
     * front of the player, so it always points at the lip the player is standing against.
     *
     * @param playerX the player's whole-pixel X
     * @return the direction that must be held
     */
    static PipeEntryDirection sidePipeDirection(final int playerX) {
        return (playerX & 0x0F) < 8 ? RIGHT : LEFT;
    }

    /**
     * @param portal the portal to check
     * @return whether the portal's type lets the player in ({@code IN} or {@code IN_AND_OUT})
     */
    static boolean isEnterable(final PipePortal portal) {
        return portal.getType() != null && portal.getType().isEnterable();
    }

    /**
     * Whether probing {@code (row, column)} reaches the pipe under the portal. Every cell of a horizontal portal
     * does. Only the anchor (lower cell) of a vertical one does, because the ROM tests the tile in front of the feet
     * against {@code TILE1_PIPEH1_B} only, the bottom half of a side pipe's lip (prg008.asm:6163).
     *
     * @param portal the portal to check
     * @param row    the probed cell's row
     * @param column the probed cell's column
     * @return {@code true} when the probe reaches the portal's pipe
     */
    static boolean isEntryCell(final PipePortal portal, final int row, final int column) {
        if (portal.getVirtualObjectType() == PIPE_PORTAL_VERTICAL) {
            return row == portal.getRow() && column == portal.getColumn();
        }
        return portal.occupies(row, column);
    }

    /**
     * The direction to hold to go into the pipe under the portal. It is worked out from the tiles each time rather
     * than stored:
     * <ul>
     *   <li>none when the portal is no longer over {@code PIPE_TERMINATION_PART} tiles (the lip was erased after the
     *   portal was placed);</li>
     *   <li>a horizontal portal: {@code DOWN} when both cells directly below it are {@code PIPE_BODY_PART} (an
     *   upright pipe), otherwise {@code UP} when both cells directly above it are (an upside-down pipe), otherwise
     *   none;</li>
     *   <li>a vertical portal: {@link #sidePipeDirection(int)}, which the ROM picks from the player's X when the
     *   player tries to go in.</li>
     * </ul>
     *
     * @param staticEnvironmentTiles the static environment layer's tiles, indexed {@code [row][column]}
     * @param portal                 the portal over the pipe's lip
     * @param playerX                the player's whole-pixel X
     * @return the direction to hold, or empty when the pipe cannot be entered
     */
    static Optional<PipeEntryDirection> entryDirection(
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

    private static boolean isPipeBodyRow(final Tile[][] tiles, final PipePortal portal, final int row) {
        if (row < 0 || row >= tiles.length || tiles[row] == null) {
            return false;
        }
        for (int column = portal.getColumn(); column < portal.getColumn() + portal.getColumns(); column++) {
            if (column < 0 || column >= tiles[row].length) {
                return false;
            }
            final Tile tile = tiles[row][column];
            if (tile == null || !tile.isOfType(PIPE_BODY_PART)) {
                return false;
            }
        }
        return true;
    }
}
