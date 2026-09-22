package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A one-way platform ("walkable top") must support the player from above only. Passing up through it is
 * exempted by {@code isSolidVert} on the strength of a rising {@code DY}, which makes the exemption
 * fragile: anything that zeroes the player's vertical velocity or claims they are grounded turns the
 * platform into a floor the player can stand on from underneath.
 *
 * <p>The grow transition used to do exactly that, which is what these tests guard.
 */
class CollisionGridOneWayPlatformTest {
    private static final double TOLERANCE = 1e-9;

    /** Row of one-way platform cells; spans pixels y = 64..79. */
    private static final int PLATFORM_ROW = 4;

    /**
     * A player Y putting the feet ({@code Y + 32} = 74) in the <b>lower</b> part of the platform cell,
     * past the 6px landing band — mid-way through rising up through the platform.
     */
    private static final int Y_INSIDE_PLATFORM = 42;

    @Test
    @DisplayName("A rising player passes up through a one-way platform")
    void risingPlayerPassesThrough() {
        // Prepare
        final PlayerRuntimeState runtimeState = airborne();
        final PlayerPosition position = positionAt(36, Y_INSIDE_PLATFORM, 0, -3.0);
        final LevelScenePlayer player = largePlayer(runtimeState, position);
        final StaticEnvironmentCollisionGrid grid = gridWithOneWayPlatformRow();

        // Sanity - the feet really are inside a one-way platform cell, so the test is not vacuous
        assertThat(grid.isOneWayTileFromPlayer(player, Offset.of(4, 32))).isTrue();
        assertThat(grid.collidesAtOffset(player, Offset.of(4, 32))).isTrue();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(position.getY()).as("Not snapped onto the platform").isCloseTo(42.0, within(TOLERANCE));
        assertThat(runtimeState.isInAir()).as("Still airborne").isTrue();
    }

    @Test
    @DisplayName("Growing mid-air inside a one-way platform does not leave the player standing under it")
    void growingInsideOneWayPlatformDoesNotCreateAFloor() {
        // Prepare - rising up through the platform when the mushroom is collected
        final PlayerRuntimeState runtimeState = airborne();
        final PlayerPosition position = positionAt(36, Y_INSIDE_PLATFORM, 1.5, -3.0);
        final LevelScenePlayer player = largePlayer(runtimeState, position);
        final StaticEnvironmentCollisionGrid grid = gridWithOneWayPlatformRow();

        // Execute - the grow starts, freezes the player for its whole duration, then completes.
        // The freeze advances no physics, so the resumed frame sees exactly the state the grow left.
        player.turnToNormal();
        for (int tick = 0; tick < PlayerRuntimeState.NORMAL_TRANSITION_TICKS; tick++) {
            runtimeState.decrementGrow();
        }
        assertThat(runtimeState.isGrowing()).as("The transition has completed").isFalse();
        grid.handleCollision(player, false);

        // Verify - the player must resume the jump, not be grounded inside the platform. A grounded
        // player here never falls (position is only advanced while airborne), which is what let them
        // walk along the platform's underside.
        assertThat(runtimeState.isInAir())
            .as("A player who grew mid-jump is still airborne afterwards")
            .isTrue();
        assertThat(runtimeState.getMovement()).isEqualTo(JUMPING);
        assertThat(position.getDY())
            .as("The jump resumes rising rather than being halted inside the platform")
            .isLessThan(0.0);
        assertThat(position.getY())
            .as("Not snapped onto the platform")
            .isCloseTo(42.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A descending player still lands on the one-way platform's top surface")
    void descendingPlayerLandsOnTop() {
        // Prepare - feet at Y + 32 = 66, inside the platform cell's top 6px landing band
        final PlayerRuntimeState runtimeState = airborne();
        final PlayerPosition position = positionAt(36, 34, 0, 2.0);
        final LevelScenePlayer player = largePlayer(runtimeState, position);
        final StaticEnvironmentCollisionGrid grid = gridWithOneWayPlatformRow();

        // Execute
        grid.handleCollision(player, false);

        // Verify - snapped so the feet rest on the platform's top edge (y = 64), and grounded
        assertThat(position.getY()).isCloseTo(32.0, within(TOLERANCE));
        assertThat(position.getDY()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(runtimeState.isInAir()).as("Landed").isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** An 8x8 grid whose row {@link #PLATFORM_ROW} is solid but passable from below. */
    private StaticEnvironmentCollisionGrid gridWithOneWayPlatformRow() {
        final LevelObject oneWayPlatform = mock(LevelObject.class);
        when(oneWayPlatform.isCollidable()).thenReturn(true);
        when(oneWayPlatform.isOneWayPlatform()).thenReturn(true);

        final LevelObject[][] objects = emptyGrid();
        Arrays.fill(objects[PLATFORM_ROW], oneWayPlatform);

        final StaticEnvironmentCollisionGrid collisionGrid = new StaticEnvironmentCollisionGrid(
            null,
            new PowerSwitchTimeWindow()
        );
        collisionGrid.setObjects(objects);
        collisionGrid.setUnderlayObjects(emptyGrid());
        collisionGrid.setDimensions(new LevelSceneDimensions(8, 8));
        return collisionGrid;
    }

    private LevelObject[][] emptyGrid() {
        final LevelObject[][] objects = new LevelObject[8][8];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private static PlayerRuntimeState airborne() {
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING);
        return runtimeState;
    }

    private static LevelScenePlayer largePlayer(
        final PlayerRuntimeState runtimeState,
        final PlayerPosition position
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.isLarge()).thenReturn(true);
        doCallRealMethod().when(player).turnToNormal();
        return player;
    }

    private static PlayerPosition positionAt(
        final double x,
        final double y,
        final double dx,
        final double dy
    ) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        position.setDX(dx);
        position.setDY(dy);
        return position;
    }
}
