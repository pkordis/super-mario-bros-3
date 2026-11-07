package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.LEFT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which pipe portals can be entered, in which direction, and the ROM's alignment window ({@code PRG008_BD1F}-
 * {@code BD4B}) and side pipe direction ({@code Pipe_PadDirForEnter}).
 */
class PipeEntryRulesAwareTest {
    private final PipeEntryRulesAware rules = new PipeEntryRulesAware() {};

    private static final int ROWS = 10;
    private static final int COLUMNS = 12;

    // The pipe's left cell is column 4, so the lip spans X 64 to 95 and the pipe's middle is X 80
    private static final int LEFT_CELL_X = 64;
    private static final int RIGHT_CELL_X = 80;
    private static final int PIPE_END_X = 96;
    private static final int WINDOW_FIRST_X = LEFT_CELL_X + 3;
    private static final int WINDOW_LAST_X = LEFT_CELL_X + 12;

    private Tile[][] staticEnvironment;

    @BeforeEach
    void prepare() {
        staticEnvironment = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : staticEnvironment) {
            Arrays.fill(row, NULL_TILE);
        }
    }

    // -------------------------------------------------------------------------
    // Enterable portals and their cells
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Only IN and IN_AND_OUT portals are ways in; an OUT portal or one without a type is not")
    void onlyInAndInAndOutAreEnterable() {
        // Execute & Verify
        assertThat(rules.isEnterable(portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 2))).isTrue();
        assertThat(rules.isEnterable(portal(IN_AND_OUT, PIPE_PORTAL_HORIZONTAL, 5, 2))).isTrue();
        assertThat(rules.isEnterable(portal(OUT, PIPE_PORTAL_HORIZONTAL, 5, 2))).isFalse();
        assertThat(rules.isEnterable(portal(null, PIPE_PORTAL_HORIZONTAL, 5, 2))).isFalse();
    }

    @Test
    @DisplayName("Every cell of a horizontal portal reaches its pipe, but only a vertical portal's lower cell does")
    void entryCells() {
        // Prepare
        final PipePortal horizontal = portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 2);
        final PipePortal vertical = portal(IN, PIPE_PORTAL_VERTICAL, 8, 5);

        // Execute & Verify
        assertThat(rules.isEntryCell(horizontal, 5, 2)).isTrue();
        assertThat(rules.isEntryCell(horizontal, 5, 3)).isTrue();
        assertThat(rules.isEntryCell(horizontal, 6, 2)).as("The pipe body below").isFalse();
        assertThat(rules.isEntryCell(vertical, 8, 5)).as("The anchor").isTrue();
        assertThat(rules.isEntryCell(vertical, 7, 5)).as("The upper half of the lip").isFalse();
    }

    // -------------------------------------------------------------------------
    // Entry direction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A horizontal portal over an upright pipe is entered going down")
    void horizontalPortalOverAnUprightPipeResolvesToDown() {
        // Prepare
        paintLip(5, 2, 3);
        paint(6, 2, PIPE_BODY_PART);
        paint(6, 3, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.entryDirection(staticEnvironment, portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 2), 40))
            .contains(DOWN);
    }

    @Test
    @DisplayName("A horizontal portal under an upside-down pipe is entered going up")
    void horizontalPortalUnderAnUpsideDownPipeResolvesToUp() {
        // Prepare
        paintLip(3, 6, 7);
        paint(2, 6, PIPE_BODY_PART);
        paint(2, 7, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.entryDirection(staticEnvironment, portal(IN, PIPE_PORTAL_HORIZONTAL, 3, 6), 104)).contains(UP);
    }

    @Test
    @DisplayName("A horizontal portal with no pipe body directly above or below both its cells has no direction")
    void horizontalPortalWithoutPipeBodyHasNoDirection() {
        // Prepare - a lip on solid ground, and a lip whose body sits under one cell only
        paintLip(5, 2, 3);
        paint(6, 2, SOLID);
        paint(6, 3, SOLID);
        paintLip(5, 8, 9);
        paint(6, 8, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.entryDirection(staticEnvironment, portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 2), 40)).isEmpty();
        assertThat(rules.entryDirection(staticEnvironment, portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 8), 136)).isEmpty();
    }

    @Test
    @DisplayName("A portal whose pipe termination tiles were erased after placing it has no direction")
    void portalOverAnErasedLipHasNoDirection() {
        // Prepare - the body is still there, the lip is gone
        paint(6, 2, PIPE_BODY_PART);
        paint(6, 3, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.entryDirection(staticEnvironment, portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 2), 40)).isEmpty();
        assertThat(rules.entryDirection(null, portal(IN, PIPE_PORTAL_HORIZONTAL, 5, 2), 40))
            .as("No static environment layer")
            .isEmpty();
    }

    @Test
    @DisplayName("A vertical portal's direction is picked from the player's X, as Pipe_PadDirForEnter does")
    void verticalPortalDirectionFollowsThePlayer() {
        // Prepare
        paint(7, 5, PIPE_TERMINATION_PART);
        paint(8, 5, PIPE_TERMINATION_PART);
        final PipePortal portal = portal(IN, PIPE_PORTAL_VERTICAL, 8, 5);

        // Execute & Verify
        assertThat(rules.entryDirection(staticEnvironment, portal, 66)).contains(RIGHT);
        assertThat(rules.entryDirection(staticEnvironment, portal, 94)).contains(LEFT);
    }

    // -------------------------------------------------------------------------
    // Exit direction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A portal on an upright pipe is come out of going up")
    void uprightPipeIsExitedUpward() {
        // Prepare
        paintLip(5, 2, 3);
        paint(6, 2, PIPE_BODY_PART);
        paint(6, 3, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_HORIZONTAL, 5, 2))).contains(UP);
    }

    @Test
    @DisplayName("A portal on an upside-down pipe is come out of going down")
    void upsideDownPipeIsExitedDownward() {
        // Prepare
        paintLip(3, 6, 7);
        paint(2, 6, PIPE_BODY_PART);
        paint(2, 7, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_HORIZONTAL, 3, 6))).contains(DOWN);
    }

    @Test
    @DisplayName("A side pipe is come out of away from its body: left when the body is to the right, and vice versa")
    void sidePipeIsExitedAwayFromItsBody() {
        // Prepare - a mouth at column 5 with the body to its right, and one at column 9 with the body to its left
        paint(7, 5, PIPE_TERMINATION_PART);
        paint(8, 5, PIPE_TERMINATION_PART);
        paint(7, 6, PIPE_BODY_PART);
        paint(8, 6, PIPE_BODY_PART);
        paint(7, 9, PIPE_TERMINATION_PART);
        paint(8, 9, PIPE_TERMINATION_PART);
        paint(7, 8, PIPE_BODY_PART);
        paint(8, 8, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_VERTICAL, 8, 5))).contains(LEFT);
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_VERTICAL, 8, 9))).contains(RIGHT);
    }

    @Test
    @DisplayName("A portal with no pipe body beside or around its lip has no exit direction")
    void portalWithoutPipeBodyHasNoExitDirection() {
        // Prepare - a horizontal lip on solid ground, and a side mouth whose body is beside one cell only
        paintLip(5, 2, 3);
        paint(6, 2, SOLID);
        paint(6, 3, SOLID);
        paint(7, 5, PIPE_TERMINATION_PART);
        paint(8, 5, PIPE_TERMINATION_PART);
        paint(8, 6, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_HORIZONTAL, 5, 2))).isEmpty();
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_VERTICAL, 8, 5))).isEmpty();
    }

    @Test
    @DisplayName("A portal whose lip was erased has no exit direction")
    void erasedLipHasNoExitDirection() {
        // Prepare - the body is still there, the lip is gone
        paint(6, 2, PIPE_BODY_PART);
        paint(6, 3, PIPE_BODY_PART);

        // Execute & Verify
        assertThat(rules.exitDirection(staticEnvironment, portal(OUT, PIPE_PORTAL_HORIZONTAL, 5, 2))).isEmpty();
        assertThat(rules.exitDirection(null, portal(OUT, PIPE_PORTAL_HORIZONTAL, 5, 2)))
            .as("No static environment layer")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Alignment window and side pipe direction
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "airborne={0}, small={1}, probe at X+{2}")
    @CsvSource({
        // Grounded: the left foot, X+4 (offset 4 for big and small alike)
        "false, false, 4",
        "false, true, 4",
        // Airborne: the head, X+8 (offset 8)
        "true, false, 8",
        "true, true, 8"
    })
    @DisplayName("Over the pipe, the player is aligned exactly from 3 to 12 px past the lip's left edge")
    void theWindowIsTenPixelsAroundThePipesMiddle(final boolean airborne, final boolean small, final int probeX) {
        // Execute & Verify - every X that puts the probe on the lip
        for (int playerX = LEFT_CELL_X - probeX; playerX < PIPE_END_X - probeX; playerX++) {
            final int probe = playerX + probeX;
            final boolean leftCell = probe < RIGHT_CELL_X;
            final boolean expected = playerX >= WINDOW_FIRST_X && playerX <= WINDOW_LAST_X;
            assertThat(rules.isAligned(playerX, airborne, small, leftCell))
                .as("Player X %d, probing the %s cell", playerX, leftCell ? "left" : "right")
                .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("The window keeps its place on every pipe, not just one a tile pair away from the origin")
    void theWindowRepeatsAcrossTheLevel() {
        // Execute & Verify - a pipe whose left cell is column 9 (an odd column), grounded and big
        assertThat(rules.isAligned(16 * 9 + 2, false, false, true)).isFalse();
        assertThat(rules.isAligned(16 * 9 + 3, false, false, true)).isTrue();
        assertThat(rules.isAligned(16 * 9 + 11, false, false, true)).isTrue();
        assertThat(rules.isAligned(16 * 9 + 12, false, false, false))
            .as("Left foot already on the right cell")
            .isTrue();
        assertThat(rules.isAligned(16 * 9 + 13, false, false, false)).isFalse();
    }

    @Test
    @DisplayName("A side pipe is entered holding RIGHT from a tile's left half and LEFT from its right half")
    void sidePipeDirectionFollowsTheHalfOfTheTile() {
        // Execute & Verify
        assertThat(rules.sidePipeDirection(194)).as("X & 15 = 2").isEqualTo(RIGHT);
        assertThat(rules.sidePipeDirection(199)).as("X & 15 = 7").isEqualTo(RIGHT);
        assertThat(rules.sidePipeDirection(200)).as("X & 15 = 8").isEqualTo(LEFT);
        assertThat(rules.sidePipeDirection(206)).as("X & 15 = 14").isEqualTo(LEFT);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void paintLip(final int row, final int leftColumn, final int rightColumn) {
        paint(row, leftColumn, PIPE_TERMINATION_PART);
        paint(row, rightColumn, PIPE_TERMINATION_PART);
    }

    private void paint(final int row, final int column, final TileType type) {
        staticEnvironment[row][column] = Tile.builder().id(100 + row * COLUMNS + column).type(type).build();
    }

    private static PipePortal portal(
        final PipePortalType type,
        final VirtualObjectType virtualObjectType,
        final int row,
        final int column
    ) {
        return PipePortal.builder()
            .type(type)
            .virtualObjectType(virtualObjectType)
            .row(row)
            .column(column)
            .build();
    }
}
