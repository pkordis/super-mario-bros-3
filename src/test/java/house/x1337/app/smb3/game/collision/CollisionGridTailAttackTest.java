package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the raccoon tail attack against static terrain to the SMB3 disassembly
 * (prg008 {@code Player_TailAttack_HitBlocks}).
 *
 * <p>The ROM does <b>not</b> sweep a box across the terrain. It loads the
 * {@code Player_TailAttack_Offsets} pair (Y = 28; X = -6 facing left, +21 facing right) into the tile
 * probe inputs and calls {@code Player_GetTileAndSlope}, which resolves exactly <b>one</b> tile. Only
 * that tile is written to the tail's {@code Level_Tile_Whack} slot and passed to
 * {@code Level_DoBumpBlocks}.
 *
 * <p>The regression these tests guard: the terrain test used to reuse the tail's <em>object</em>
 * hitbox ({@code Object_RespondToTailAttack}: 10 wide, 15 tall at +16). Being 15px tall, that box
 * straddled two tile rows whenever the player was not tile-aligned. The worst case is a player whose
 * head is pressed against a ceiling: the 6px head padding puts the sprite top 6px above the row
 * boundary, dragging the box's top edge into the row above, so the tail broke a brick level with the
 * player's chest — something the original never does.
 */
class CollisionGridTailAttackTest {

    /** Sprite-pixel Y offset of the tail's terrain probe (dasm {@code Player_TailAttack_Offsets}). */
    private static final double PROBE_Y_OFFSET = 28;
    /** Sprite-pixel X offset of the tail's terrain probe when the player faces right. */
    private static final double PROBE_X_OFFSET_RIGHT = 21;
    private static final int TILE = 16;

    @Test
    @DisplayName("Tail strikes only the tile containing the probe point, never the row above")
    void tailHitsSingleRowWhileHeadIsAgainstCeiling() {
        // Prepare
        // Ceiling block occupies row 2; the player stands in the two-tile gap of rows 3 and 4, with
        // row 4 ("A") touching the ground at row 5 and row 3 ("B") above it. Head flush to the
        // ceiling means the hitbox top (y + 6) sits on row 3's top edge (3 * 16 = 48), so y = 42.
        final double playerY = 3 * TILE - 6;
        final double playerX = 2 * TILE;
        // Probe lands at y + 28 = 70 -> row 4 (feet), and x + 21 = 53 -> column 3.
        final double probeY = playerY + PROBE_Y_OFFSET;
        final double probeX = playerX + PROBE_X_OFFSET_RIGHT;
        final int probedRow = (int) Math.floor(probeY / TILE);
        final int probedColumn = (int) Math.floor(probeX / TILE);

        final LevelObject brickAtChestLevel = collidableMock();
        final LevelObject brickAtFeetLevel = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        objects[probedRow - 1][probedColumn] = brickAtChestLevel;
        objects[probedRow][probedColumn] = brickAtFeetLevel;

        final LevelScenePlayer player = playerProbingAt(probeX, probeY);
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.resolveTailAttack(player, probeX, probeY);

        // Verify
        verify(brickAtFeetLevel).onTailAttack(player);
        verify(brickAtChestLevel, never()).onTailAttack(player);
    }

    @Test
    @DisplayName("Tail reaches the column beside the player, not the one it stands in")
    void tailStrikesTheFacedColumnOnly() {
        // Prepare
        // Player occupies column 2 (x = 32..47); the probe at x + 21 = 53 falls in column 3.
        final double playerX = 2 * TILE;
        final double playerY = 4 * TILE;
        final double probeX = playerX + PROBE_X_OFFSET_RIGHT;
        final double probeY = playerY + PROBE_Y_OFFSET;

        final LevelObject brickUnderPlayer = collidableMock();
        final LevelObject brickBesidePlayer = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        final int probedRow = (int) Math.floor(probeY / TILE);
        objects[probedRow][2] = brickUnderPlayer;
        objects[probedRow][3] = brickBesidePlayer;

        final LevelScenePlayer player = playerProbingAt(probeX, probeY);
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.resolveTailAttack(player, probeX, probeY);

        // Verify
        verify(brickBesidePlayer).onTailAttack(player);
        verify(brickUnderPlayer, never()).onTailAttack(player);
    }

    @Test
    @DisplayName("An empty probed cell dispatches nothing")
    void emptyCellIsIgnored() {
        // Prepare
        final LevelObject brick = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        objects[6][6] = brick;

        final LevelScenePlayer player = playerProbingAt(8, 8);
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.resolveTailAttack(player, 8, 8);

        // Verify
        verify(brick, never()).onTailAttack(player);
    }

    @Test
    @DisplayName("A probe outside the grid is harmless")
    void outOfBoundsProbeIsIgnored() {
        // Prepare
        final LevelScenePlayer player = playerProbingAt(-40, -40);
        final StaticEnvironmentCollisionGrid grid = gridFor(emptyGrid());

        // Execute & Verify - the out-of-range lookup returns the shared empty sentinel, so this must
        // neither throw nor dispatch.
        grid.resolveTailAttack(player, -40, -40);
        grid.resolveTailAttack(player, 4000, 4000);
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

    private StaticEnvironmentCollisionGrid gridFor(final LevelObject[][] objects) {
        final StaticEnvironmentCollisionGrid collisionGrid = new StaticEnvironmentCollisionGrid(
            null,
            new PowerSwitchTimeWindow()
        );
        collisionGrid.setObjects(objects);
        collisionGrid.setUnderlayObjects(emptyGrid());
        collisionGrid.setDimensions(new LevelSceneDimensions(8, 8));
        return collisionGrid;
    }

    private LevelScenePlayer playerProbingAt(final double probeX, final double probeY) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getTailAttackBlockProbeX()).thenReturn(probeX);
        when(player.getTailAttackBlockProbeY()).thenReturn(probeY);
        return player;
    }
}
