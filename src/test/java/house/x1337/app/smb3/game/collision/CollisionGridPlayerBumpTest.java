package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A player whose feet are on a block bumped from below is thrown up off it.
 */
class CollisionGridPlayerBumpTest {
    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("A player standing on a bumped block is thrown up at 3 px a frame, off the ground")
    void aPlayerOnABumpedBlockIsThrownUp() {
        // Prepare
        final PlayerPosition position = positionAt(32, 64);
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.stop();
        final LevelScenePlayer player = player(position, runtimeState);
        final StaticEnvironmentCollisionGrid grid = gridWith(player);
        doReturn(true).when(grid).isBlockBumpActiveAt(of(2, 6));

        // Execute
        grid.bumpThePlayersOnBumpedBlocks();

        // Verify
        assertThat(position.getDY()).isCloseTo(-3.0, within(TOLERANCE));
        assertThat(runtimeState.getMovement()).isEqualTo(JUMPING);
    }

    @Test
    @DisplayName("A player standing beside a bumped block is left alone")
    void aPlayerBesideABumpedBlockIsLeftAlone() {
        // Prepare
        final LevelScenePlayer player = player(positionAt(32, 64), new PlayerRuntimeState());
        final StaticEnvironmentCollisionGrid grid = gridWith(player);
        doReturn(true).when(grid).isBlockBumpActiveAt(of(3, 6));

        // Execute
        grid.bumpThePlayersOnBumpedBlocks();

        // Verify
        verify(player, never()).getBumpedFromBelow();
    }

    @Test
    @DisplayName("A dying player is not thrown")
    void aDyingPlayerIsNotThrown() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.startDying();
        final LevelScenePlayer player = player(positionAt(32, 64), runtimeState);
        final StaticEnvironmentCollisionGrid grid = gridWith(player);
        doReturn(true).when(grid).isBlockBumpActiveAt(any());

        // Execute
        grid.bumpThePlayersOnBumpedBlocks();

        // Verify
        verify(player, never()).getBumpedFromBelow();
    }

    private StaticEnvironmentCollisionGrid gridWith(final LevelScenePlayer player) {
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getAllPlayers()).thenReturn(List.<Player>of(player));
        return spy(new StaticEnvironmentCollisionGrid(gameEngine, new PowerSwitchTimeWindow()));
    }

    private LevelScenePlayer player(final PlayerPosition position, final PlayerRuntimeState runtimeState) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        doCallRealMethod().when(player).getBumpedFromBelow();
        return player;
    }

    private PlayerPosition positionAt(final double x, final double y) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        return position;
    }
}
