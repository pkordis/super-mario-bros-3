package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.DOWN;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A solid tile that lets players through, as a sinking note block does, is thin air to a player's feet and stays solid
 * ground to everything else.
 */
class CollisionGridPassableTileTest {
    private static final int SIZE = 8;
    private static final Coordinates TILE = of(2, 4);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("A falling player lands on a solid tile unless it lets players through")
    void aPlayerLandsUnlessTheTileLetsThemThrough(final boolean passable) {
        // Prepare
        final StaticEnvironmentCollisionGrid grid = gridWith(solidTile(passable));
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(FALLING);
        final PlayerPosition position = new PlayerPosition();
        position.setX(TILE.x() * 16);
        position.setY(TILE.y() * 16 - 30);
        position.setDY(2.0);
        final LevelScenePlayer player = smallPlayer(position, runtimeState);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(runtimeState.isInAir()).as("Still falling").isEqualTo(passable);
        assertThat(grid.isGroundSolidTile(TILE)).as("Ground to enemies and power-ups").isTrue();
        assertThat(grid.isWallOrCeilingSolidTile(TILE)).as("A wall to enemies and power-ups").isTrue();
    }

    private LevelObject solidTile(final boolean passable) {
        final LevelObject tile = mock(LevelObject.class);
        when(tile.isCollidable()).thenReturn(true);
        when(tile.isPassableForPlayers()).thenReturn(passable);
        return tile;
    }

    private StaticEnvironmentCollisionGrid gridWith(final LevelObject tile) {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(SIZE, SIZE));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);

        final LevelObject[][] objects = emptyGrid();
        objects[TILE.y()][TILE.x()] = tile;
        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            gameEngine,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(objects);
        grid.setUnderlayObjects(emptyGrid());
        grid.setDimensions(new LevelSceneDimensions(SIZE, SIZE));
        return grid;
    }

    private LevelObject[][] emptyGrid() {
        final LevelObject[][] objects = new LevelObject[SIZE][SIZE];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private LevelScenePlayer smallPlayer(final PlayerPosition position, final PlayerRuntimeState runtimeState) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, DOWN));
        when(player.isLarge()).thenReturn(false);
        return player;
    }
}
