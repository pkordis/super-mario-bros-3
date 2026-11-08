package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.enumeration.PlayerMovement;
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
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The collision pass flags a head strike for the pipe entry check (dasm {@code Player_HitCeiling}).
 */
class CollisionGridHitCeilingTest {
    private static final int SIZE = 8;
    private static final int CEILING_ROW = 2;
    private static final int CEILING_COLUMN = 2;

    @Test
    @DisplayName("Rising into a solid tile flags the ceiling hit")
    void risingIntoASolidTileFlagsTheHit() {
        // Prepare - a big player's head probe (X+8, Y+6) lands on the ceiling cell
        final LevelScenePlayer player = largePlayer(JUMPING, 32, 34, -2);
        final StaticEnvironmentCollisionGrid grid = gridWithACeiling();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(player.getRuntimeState().isHitCeiling()).isTrue();
    }

    @Test
    @DisplayName("Rising into a solid tile records the head probe's Y, which a coin out of the block launches from")
    void aHeadHitRecordsTheProbeY() {
        // Prepare - the big player's head probe is Y + 6, at 34.75 + 6 floored
        final LevelScenePlayer player = largePlayer(JUMPING, 32, 34.75, -2);
        final StaticEnvironmentCollisionGrid grid = gridWithACeiling();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(grid.getBumpProbeY()).isEqualTo(40);
    }

    @Test
    @DisplayName("Every collision pass starts by clearing the flag, so it lasts one tick")
    void everyPassClearsTheFlag() {
        // Prepare - flagged by an earlier tick, falling through open air now
        final LevelScenePlayer player = largePlayer(FALLING, 96, 34, 1);
        player.getRuntimeState().setHitCeiling(true);
        final StaticEnvironmentCollisionGrid grid = gridWithACeiling();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(player.getRuntimeState().isHitCeiling()).isFalse();
    }

    @Test
    @DisplayName("Rising through open air does not flag a ceiling hit")
    void risingThroughOpenAirDoesNotFlag() {
        // Prepare
        final LevelScenePlayer player = largePlayer(JUMPING, 96, 34, -2);
        final StaticEnvironmentCollisionGrid grid = gridWithACeiling();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(player.getRuntimeState().isHitCeiling()).isFalse();
    }

    private StaticEnvironmentCollisionGrid gridWithACeiling() {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);
        final LevelObject[][] objects = emptyCells();
        objects[CEILING_ROW][CEILING_COLUMN] = solid;

        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            null,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(objects);
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(SIZE, SIZE));
        return grid;
    }

    private static LevelObject[][] emptyCells() {
        final LevelObject[][] objects = new LevelObject[SIZE][SIZE];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private static LevelScenePlayer largePlayer(
        final PlayerMovement movement,
        final double x,
        final double y,
        final double dy
    ) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        position.setDY(dy);
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(movement);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, UP));
        when(player.isLarge()).thenReturn(true);
        return player;
    }
}
