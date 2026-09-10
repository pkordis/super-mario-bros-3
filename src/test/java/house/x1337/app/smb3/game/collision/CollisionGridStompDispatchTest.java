package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code onCollisionFromAbove} ("stomp") dispatch in
 * {@link StaticEnvironmentCollisionGrid#handleCollision}.
 *
 * <p>The ROM's press test (dasm prg008 {@code PRG008_B623}) runs {@code CPX #$02 / BGS} to explicitly
 * <b>reject</b> tile detections that came from the player's head probes, leaving only the feet/body
 * probes able to trigger it. These tests hold us to the same split: landing on a tile notifies it from
 * above, head-butting a tile from below does not.
 *
 * <p>Probe geometry comes from {@code CollisionOffsets.LARGE_PROBES}: descending, the vertical probe
 * pair is the two feet at {@code ($04,$20)} and {@code ($0B,$20)}; rising, it is the single head point
 * at {@code ($08,$06)} repeated.
 */
class CollisionGridStompDispatchTest {

    private static final int TILE = 16;

    @Test
    @DisplayName("Standing on a tile notifies it from above")
    void standingOnTileDispatchesFromAbove() {
        // Prepare
        // Player at x = 36 -> both feet probes (x + 4 = 40, x + 11 = 47) fall in column 2.
        // y = 32 -> feet at y + 32 = 64, i.e. row 4.
        final LevelObject floor = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        objects[4][2] = floor;

        final LevelScenePlayer player = groundedPlayer(positionAt(36, 32));
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        verify(floor).onCollisionFromAbove(player);
        verify(floor, never()).onCollisionFromBelow(player);
    }

    @Test
    @DisplayName("Feet straddling two tiles notify both, once each")
    void straddlingFeetDispatchToBothTiles() {
        // Prepare
        // x = 42 -> left foot at 46 (column 2), right foot at 53 (column 3).
        final LevelObject leftTile = collidableMock();
        final LevelObject rightTile = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        objects[4][2] = leftTile;
        objects[4][3] = rightTile;

        final LevelScenePlayer player = groundedPlayer(positionAt(42, 32));
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        verify(leftTile).onCollisionFromAbove(player);
        verify(rightTile).onCollisionFromAbove(player);
    }

    @Test
    @DisplayName("Both feet on one tile notify it exactly once, not twice")
    void singleTileUnderBothFeetIsNotifiedOnce() {
        // Prepare
        final LevelObject floor = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        objects[4][2] = floor;

        final LevelScenePlayer player = groundedPlayer(positionAt(36, 32));
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.handleCollision(player, false);

        // Verify - Mockito's default verify() is times(1), so a duplicate dispatch fails here.
        verify(floor).onCollisionFromAbove(player);
    }

    @Test
    @DisplayName("Head-butting a tile from below never notifies it from above (ROM rejects head probes)")
    void risingIntoTileDoesNotDispatchFromAbove() {
        // Prepare
        // Rising: the vertical probe is the head at y + 6. y = 74 -> 80, i.e. row 5.
        final LevelObject ceiling = collidableMock();
        final LevelObject[][] objects = emptyGrid();
        objects[5][2] = ceiling;

        final LevelScenePlayer player = risingPlayer(positionAt(36, 74));
        final StaticEnvironmentCollisionGrid grid = gridFor(objects);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        verify(ceiling).onCollisionFromBelow(player);
        verify(ceiling, never()).onCollisionFromAbove(player);
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

    private LevelScenePlayer groundedPlayer(final PlayerPosition position) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        when(player.isLarge()).thenReturn(true);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, UP));
        return player;
    }

    private LevelScenePlayer risingPlayer(final PlayerPosition position) {
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.isLarge()).thenReturn(true);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, UP));
        position.setDY(-2);
        return player;
    }

    private PlayerPosition positionAt(final double x, final double y) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        return position;
    }
}
