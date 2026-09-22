package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The raccoon tail attack against a Goomba: flashed, flipped and thrown off the level rather than
 * flattened, and worth the same 100 points.
 *
 * <p>ROM chain: {@code Object_RespondToTailAttack} (dasm prg000 @ PRG000_DB7A) seeds the flash off the
 * player's own position and falls into {@code Enemy_Kill}, which scores it and puts it in
 * {@code ObjState_Killed}.
 */
class GoombaTailAttackTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int WALK_FRAME_COUNT = 2;

    /** {@code Player_X ± $10} / {@code Player_Y + $10}. */
    private static final double TAIL_OFFSET = 0x10;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private TailAttackFlashMotionManager tailAttackFlashMotionManager;
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
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        tailAttackFlashMotionManager = mock(TailAttackFlashMotionManager.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
            .thenReturn(animator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TailAttackFlashMotionManager.class))
            .thenReturn(tailAttackFlashMotionManager);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A tail attack kills the Goomba outright rather than squishing it")
    void tailAttackKillsRatherThanSquishes() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        assertThat(goomba.isHittable()).isTrue();

        // Execute
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Verify
        assertThat(goomba.isSquished()).as("not the stomp death").isFalse();
        assertThat(goomba.getWhamMotion()).as("thrown off the level instead").isNotNull();
        assertThat(goomba.isHittable()).as("intangible from the blow onwards").isFalse();
    }

    @Test
    @DisplayName("A tail attack queues the same 100-point caption a stomp does")
    void tailAttackQueuesOneHundredPoints() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        assertThat(goomba.getPendingScoreReward()).isNull();

        // Execute
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Verify - Enemy_Kill calls Score_Get100PlusPts, the same award as the press branch
        assertThat(goomba.getPendingScoreReward()).isEqualTo(SCORE_100);
    }

    @Test
    @DisplayName("Attacking from the left puts the flash on the player's right, between the two")
    void flashLandsBetweenThePlayerAndAGoombaOnTheRight() {
        // Prepare - the Goomba is to the player's right
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final double playerX = 4 * TILE_SPRITE_SIZE;

        // Execute
        goomba.onTailAttack(playerAt(playerX, WALK_ROW_PIXEL_Y, RIGHT));

        // Verify
        final Offset spawnedAt = captureFlashOffset();
        assertThat(spawnedAt.x())
            .as("Player_X + $10, on the struck side")
            .isEqualTo((int) (playerX + TAIL_OFFSET));
        assertThat(spawnedAt.y())
            .as("Player_Y + $10")
            .isEqualTo((int) (WALK_ROW_PIXEL_Y + TAIL_OFFSET));
        assertThat(spawnedAt.x())
            .as("between the player and the Goomba")
            .isBetween((int) playerX, (int) goomba.getPixelX() + TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Attacking from the right puts the flash on the player's left, still between the two")
    void flashLandsBetweenThePlayerAndAGoombaOnTheLeft() {
        // Prepare - the Goomba is to the player's left this time
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final double playerX = 7 * TILE_SPRITE_SIZE;

        // Execute
        goomba.onTailAttack(playerAt(playerX, WALK_ROW_PIXEL_Y, LEFT));

        // Verify
        final Offset spawnedAt = captureFlashOffset();
        assertThat(spawnedAt.x()).isEqualTo((int) (playerX - TAIL_OFFSET));
        assertThat(spawnedAt.x())
            .as("between the Goomba and the player")
            .isBetween((int) goomba.getPixelX(), (int) playerX);
    }

    @Test
    @DisplayName("The struck side wins over the player's facing, since the tail sweeps a full arc")
    void flashFollowsTheStruckSideNotTheFacing() {
        // Prepare - a Goomba on the right, clipped by a player already facing away to the left
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final double playerX = 4 * TILE_SPRITE_SIZE;

        // Execute
        goomba.onTailAttack(playerAt(playerX, WALK_ROW_PIXEL_Y, LEFT));

        // Verify - still flashes towards the Goomba rather than off the player's far shoulder
        assertThat(captureFlashOffset().x()).isEqualTo((int) (playerX + TAIL_OFFSET));
    }

    @Test
    @DisplayName("The Goomba holds its ground for one tick, then flips over and is thrown")
    void flipAndLaunchLandOnTheSecondTick() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));
        final double struckAtX = goomba.getPixelX();
        final double struckAtY = goomba.getPixelY();

        // Execute - the tick the flash appears on
        goomba.motionUpdate();

        // Verify - still exactly where it was hit, and still upright
        assertThat(goomba.getPixelX()).isCloseTo(struckAtX, within(TOLERANCE));
        assertThat(goomba.getPixelY()).isCloseTo(struckAtY, within(TOLERANCE));
        assertThat(goomba.getWhamMotion().isUpsideDown()).isFalse();

        // Execute - wham frame _1
        goomba.motionUpdate();

        // Verify - inverted and rising
        assertThat(goomba.getWhamMotion().isUpsideDown()).isTrue();
        assertThat(goomba.getPixelY()).as("thrown upward").isLessThan(struckAtY);
        assertThat(goomba.getPixelX()).as("and knocked away from the player").isGreaterThan(struckAtX);
    }

    @Test
    @DisplayName("A tail-struck Goomba falls straight through the floor instead of landing on it")
    void killedGoombaIgnoresTerrain() {
        // Prepare - the distinguishing behaviour against a squish: ObjState_Killed runs Object_Move with
        // no ground alignment, so the corpse sinks through the platform it was walking on. Walk first, so
        // it is genuinely resting on that floor when the blow lands.
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.motionUpdate();
        assertThat(goomba.isGrounded()).as("standing on the floor when struck").isTrue();
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Execute - long enough to clear the apex and sink past the floor row
        double deepest = goomba.getPixelY();
        for (int tick = 0; tick < 80; tick++) {
            goomba.motionUpdate();
            deepest = max(deepest, goomba.getPixelY());
        }

        // Verify - it went clean through the row it had been standing on
        assertThat(deepest)
            .as("sank below the floor it used to stand on")
            .isGreaterThan(FLOOR_ROW * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("It expires once it has fallen off the bottom of the level")
    void killedGoombaExpiresBelowTheLevel() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Execute - Object_FallAndDelete removes it once it is far below the level
        for (int tick = 0; tick < 200; tick++) {
            goomba.motionUpdate();
            if (goomba.isExpired()) {
                break;
            }
        }

        // Verify
        assertThat(goomba.isExpired()).isTrue();
    }

    @Test
    @DisplayName("A second tail attack neither re-flashes nor pays out again")
    void secondTailAttackIsIgnored() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));
        final EnemyWhamMotionHandle firstThrow = new EnemyWhamMotionHandle(goomba);
        goomba.setPendingScoreReward(null);

        // Execute
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Verify
        assertThat(goomba.getPendingScoreReward()).as("paid once").isNull();
        assertThat(firstThrow.isSameAsCurrent(goomba)).as("the original throw was not restarted").isTrue();
        verify(tailAttackFlashMotionManager, times(1).description("one flash only"))
            .spawn(eq(gameEngine), any(Offset.class));
    }

    @Test
    @DisplayName("A tail attack on an already-flattened Goomba does nothing")
    void tailAttackOnSquishedGoombaIsIgnored() {
        // Prepare - stomped first
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.onCollisionFromAbove(playerAt(5 * TILE_SPRITE_SIZE, 0, RIGHT));
        goomba.setPendingScoreReward(null);

        // Execute
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Verify - it sees out its squish, unflipped and unpaid
        assertThat(goomba.getWhamMotion()).isNull();
        assertThat(goomba.getPendingScoreReward()).isNull();
        assertThat(goomba.isSquished()).isTrue();
        verify(tailAttackFlashMotionManager, never()).spawn(eq(gameEngine), any(Offset.class));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** @return the single point the flash was spawned at */
    private Offset captureFlashOffset() {
        final ArgumentCaptor<Offset> offsetCaptor = ArgumentCaptor.forClass(Offset.class);
        verify(tailAttackFlashMotionManager).spawn(eq(gameEngine), offsetCaptor.capture());
        return offsetCaptor.getValue();
    }

    /** Remembers which throw a Goomba was given, so a restart is detectable by identity. */
    private record EnemyWhamMotionHandle(Object motion) {        EnemyWhamMotionHandle(final Goomba goomba) {
            this((Object) goomba.getWhamMotion());
        }

        boolean isSameAsCurrent(final Goomba goomba) {
            return motion == goomba.getWhamMotion();
        }
    }

    private Goomba goombaAt(final int column, final int row) {
        final Goomba goomba = new Goomba(gameEngine, Offset.of(column, row));
        goomba.init();
        return goomba;
    }

    /** A raccoon player at the given position, facing the given way. */
    private static LevelScenePlayer playerAt(
        final double pixelX,
        final double pixelY,
        final PlayerOrientationHorizontal facing
    ) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(pixelY);
        position.setDY(1.0);

        final PlayerOrientation orientation = mock(PlayerOrientation.class);
        when(orientation.getHorizontal()).thenReturn(facing);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getOrientation()).thenReturn(orientation);
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
