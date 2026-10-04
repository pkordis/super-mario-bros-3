package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Goomba hurts the player on every contact that is not a stomp, and is itself unharmed by them.
 *
 * <p>The ROM decides this with one comparison and no horizontal test at all ({@code Player_HitEnemy},
 * dasm prg000 @ PRG000_D218): fail the stomp depth check and control reaches PRG000_D355, "Player
 * potentially gonna get hurt". So side contact and being jumped into from below are the same case, and
 * the engine's two separate callbacks for them must both lead to the player's hurt path.
 */
class GoombaHurtsPlayerTest {
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private boolean[][] solids;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }

        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        final GoombaAnimator animator = mock(GoombaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(2);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
            .thenReturn(animator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Walking into a Goomba's side hurts the player and leaves the Goomba walking")
    void sideContactHurtsThePlayer() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final LevelScenePlayer player = playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y);

        // Execute
        goomba.onPlayerOverlap(player);

        // Verify
        verify(player).onHurt();
        assertThat(goomba.isSquished()).as("side contact costs the Goomba nothing").isFalse();
        assertThat(goomba.getPendingScoreReward()).as("and earns no points").isNull();
        assertThat(goomba.isHittable()).isTrue();
    }

    @Test
    @DisplayName("Jumping up into a Goomba from below hurts the player just the same")
    void contactFromBelowHurtsThePlayer() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final LevelScenePlayer player = playerAt(5 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y + TILE_SPRITE_SIZE);

        // Execute
        goomba.onCollisionFromBelow(player);

        // Verify
        verify(player).onHurt();
        assertThat(goomba.isSquished()).isFalse();
        assertThat(goomba.getPendingScoreReward()).isNull();
    }

    @Test
    @DisplayName("A stomp from above still squishes and scores, and never hurts the player")
    void stompFromAboveIsUnchanged() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final LevelScenePlayer player = playerAt(5 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y - TILE_SPRITE_SIZE);

        // Execute
        goomba.onCollisionFromAbove(player);

        // Verify - the ROM's stomp branch never reaches the hurt code
        verify(player, never()).onHurt();
        assertThat(goomba.isSquished()).isTrue();
        assertThat(goomba.getPendingScoreReward()).isNotNull();
    }

    @Test
    @DisplayName("A Goomba defeated earlier in the same tick can no longer hurt anyone")
    void aDefeatedGoombaCannotHurt() {
        // isHittable keeps it out of the broadphase from the next tick, but it was inserted while alive,
        // so the pass running now can still reach it.
        // Prepare - flattened by a stomp this tick
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.onCollisionFromAbove(playerAt(5 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y - TILE_SPRITE_SIZE));

        final LevelScenePlayer secondPlayer = playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y);

        // Execute
        goomba.onPlayerOverlap(secondPlayer);
        goomba.onCollisionFromBelow(secondPlayer);

        // Verify
        verify(secondPlayer, never()).onHurt();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Goomba goombaAt(final int column, final int row) {
        final Goomba goomba = new Goomba(gameEngine, Offset.of(column, row));
        goomba.init();
        return goomba;
    }

    private static LevelScenePlayer playerAt(final double pixelX, final double pixelY) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(pixelY);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        return player;
    }

    /** Mirrors {@code StaticEnvironmentCollisionGrid#isSolidTile}: outside the level is solid, sky is not. */
    private boolean isSolid(final int column, final int row) {
        if (row < 0) {
            return false;
        }
        if (row >= ROWS || column < 0 || column >= COLUMNS) {
            return true;
        }
        return solids[row][column];
    }
}
