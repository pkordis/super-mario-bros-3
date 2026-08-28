package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the horizontal wall/corner ejection in {@link CollisionGrid#handleCollision}
 * against the SMB3 disassembly (prg008 Player_DetectSolids @ PRG008_B4F3) and its
 * JS reference port (smb3dasm/index.html "eject player from wall").
 *
 * <p>The original nudges the player 1px/frame toward tile alignment whenever an
 * in-front probe detects a solid tile — regardless of horizontal velocity. This is
 * what lets the player slide off the corner of a block they jump into at its very
 * edge (a straight-up jump has DX == 0). Velocity is halted only when the player is
 * actually pushing into the wall. When the facing edge is already tile-aligned no
 * correction runs at all, which is what keeps a flush, stationary player (the frame
 * after an emexit ends) from being re-snapped and frozen.
 */
class CollisionGridWallNudgeTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("Straight-up jump at a block edge slides the player 1px toward the free side (DX == 0)")
    void cornerSlideHappensWithZeroHorizontalVelocity() {
        // Prepare
        // Left half of tile (X mod 16 = 4 < 8): in-front probes look RIGHT
        // (X + 0x0E = 50 -> column 3). A solid column at 3 is the block edge.
        // The right edge (X + 14 = 50, localX = 2) is not tile-aligned, so the
        // player must be nudged left by 1px even though DX == 0.
        final PlayerPosition position = positionAt(36, 32, 0, -2);
        final LevelScenePlayer player = largePlayerMovingUp(position);
        final CollisionGrid grid = gridFor(player, gridWithSolidColumn(3));

        // Execute
        final boolean hitWall = grid.handleCollision(10, false);

        // Verify
        assertThat(hitWall).as("Touching the block edge registers as a wall hit").isTrue();
        assertThat(position.getX()).as("Player slides 1px toward the free side").isCloseTo(35.0, within(TOLERANCE));
        assertThat(position.getDX()).as("No horizontal velocity is introduced").isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Walking into a wall nudges to alignment and halts horizontal velocity")
    void walkingIntoWallStops() {
        // Prepare
        final PlayerPosition position = positionAt(36, 32, 2.0, -2);
        final LevelScenePlayer player = largePlayerMovingUp(position);
        final CollisionGrid grid = gridFor(player, gridWithSolidColumn(3));

        // Execute
        grid.handleCollision(10, false);

        // Verify
        assertThat(position.getX()).as("Player is pushed off the wall").isCloseTo(35.0, within(TOLERANCE));
        assertThat(position.getDX()).as("Velocity into the wall is halted").isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Sliding in the ejection direction preserves horizontal velocity")
    void slideDoesNotStopVelocityMovingAwayFromWall() {
        // Prepare
        // Wall on the right (left half), ejection dir = -1. Moving left (DX < 0)
        // is not "into" the wall, so velocity is preserved while still nudging.
        final PlayerPosition position = positionAt(36, 32, -1.5, -2);
        final LevelScenePlayer player = largePlayerMovingUp(position);
        final CollisionGrid grid = gridFor(player, gridWithSolidColumn(3));

        // Execute
        grid.handleCollision(10, false);

        // Verify
        assertThat(position.getX()).as("Player still slides 1px").isCloseTo(35.0, within(TOLERANCE));
        assertThat(position.getDX()).as("Velocity away from the wall is preserved").isCloseTo(-1.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("A flush, tile-aligned stationary player is never re-snapped or frozen (emexit regression)")
    void flushAlignedPlayerIsNotReSnapped() {
        // Prepare
        // X = 34 -> right edge X + 14 = 48, localX = 0 (tile-aligned/flush).
        // This is the post-emexit condition: DX == 0, resting against the wall.
        final PlayerPosition position = positionAt(34, 32, 0, -2);
        final LevelScenePlayer player = largePlayerMovingUp(position);
        final CollisionGrid grid = gridFor(player, gridWithSolidColumn(3));

        // Execute & Verify
        // Multiple frames must not move the player nor lock its velocity — the
        // old full-snap-plus-setDX(0) approach froze the camera here.
        for (int frame = 0; frame < 3; frame++) {
            grid.handleCollision(10, false);
            assertThat(position.getX()).as("Flush player must not be re-snapped").isCloseTo(34.0, within(TOLERANCE));
            assertThat(position.getDX()).as("Flush player velocity is untouched").isCloseTo(0.0, within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("Wall correction is suppressed while low-clearance/emexit is active")
    void wallCorrectionSuppressedDuringLowClearance() {
        // Prepare
        final PlayerPosition position = positionAt(36, 32, 0, -2);
        final LevelScenePlayer player = largePlayerMovingUp(position);
        final CollisionGrid grid = gridFor(player, gridWithSolidColumn(3));

        // Execute
        final boolean hitWall = grid.handleCollision(10, true);

        // Verify
        assertThat(hitWall).as("No wall hit while suppressed").isFalse();
        assertThat(position.getX()).as("Position is untouched while suppressed").isCloseTo(36.0, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** Builds an 8x8 grid of empty tiles with a solid column at {@code solidCol}. */
    private LevelObject[][] gridWithSolidColumn(final int solidCol) {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);

        final LevelObject[][] objects = new LevelObject[8][8];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        for (int row = 0; row < 8; row++) {
            objects[row][solidCol] = solid;
        }
        return objects;
    }

    private CollisionGrid gridFor(final LevelScenePlayer player, final LevelObject[][] objects) {
        return new CollisionGrid(player, objects, new LevelSceneDimensions(8, 8), null);
    }

    private LevelScenePlayer largePlayerMovingUp(final PlayerPosition position) {
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING); // airborne so the "moving up" branch is taken

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.isLarge()).thenReturn(true);
        return player;
    }

    private PlayerPosition positionAt(final double x, final double y, final double dx, final double dy) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        position.setDX(dx);
        position.setDY(dy);
        return position;
    }
}
