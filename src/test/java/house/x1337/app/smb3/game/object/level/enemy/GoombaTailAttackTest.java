package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_200;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_400;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_800;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
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
 * <p>The strike seeds the flash off the player's own position, then scores the Goomba and puts it in the
 * killed state.
 */
class GoombaTailAttackTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int WALK_FRAME_COUNT = 2;

    /** The flash sits 16 px to the struck side of the player and 16 px below its top. */
    private static final double TAIL_OFFSET = 16;

    /** The rightward knock a carried shell resolves to. */
    private static final int SHELL_KNOCK_RIGHT = 8;

    /** Aliased rather than statically imported, which would run past the 120-column limit. */
    private static final int SHELL_KILL_LAUNCH_Y = EnemyKnockMotion.SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT;

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
        when(collisionGrid.isGroundSolidTile(any())).thenAnswer(invocation -> {
            final Coordinates cell = invocation.getArgument(0);
            return isSolid(cell.x(), cell.y());
        });
        when(collisionGrid.isWallOrCeilingSolidTile(any())).thenAnswer(invocation -> {
            final Coordinates cell = invocation.getArgument(0);
            return isSolid(cell.x(), cell.y());
        });
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

        // Verify - the same award as the stomp
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
        final Coordinates spawnedAt = captureFlashOffset();
        assertThat(spawnedAt.x())
            .as("Player X + 16, on the struck side")
            .isEqualTo((int) (playerX + TAIL_OFFSET));
        assertThat(spawnedAt.y())
            .as("Player Y + 16")
            .isEqualTo((int) (WALK_ROW_PIXEL_Y + TAIL_OFFSET));
        assertThat(spawnedAt.x())
            .as("between the player and the Goomba")
            .isBetween((int) playerX, (int) goomba.getPosition().x() + TILE_SPRITE_SIZE);
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
        final Coordinates spawnedAt = captureFlashOffset();
        assertThat(spawnedAt.x()).isEqualTo((int) (playerX - TAIL_OFFSET));
        assertThat(spawnedAt.x())
            .as("between the Goomba and the player")
            .isBetween((int) goomba.getPosition().x(), (int) playerX);
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
        final double struckAtX = goomba.getPosition().x();
        final double struckAtY = goomba.getPosition().y();

        // Execute - the tick the flash appears on
        goomba.motionUpdate();

        // Verify - still exactly where it was hit, and still upright
        assertThat(goomba.getPosition().x()).isCloseTo(struckAtX, within(TOLERANCE));
        assertThat(goomba.getPosition().y()).isCloseTo(struckAtY, within(TOLERANCE));
        assertThat(goomba.getWhamMotion().isUpsideDown()).isFalse();

        // Execute - wham frame _1
        goomba.motionUpdate();

        // Verify - inverted and rising
        assertThat(goomba.getWhamMotion().isUpsideDown()).isTrue();
        assertThat(goomba.getPosition().y()).as("thrown upward").isLessThan(struckAtY);
        assertThat(goomba.getPosition().x()).as("and knocked away from the player").isGreaterThan(struckAtX);
    }

    @Test
    @DisplayName("A tail-struck Goomba falls straight through the floor instead of landing on it")
    void killedGoombaIgnoresTerrain() {
        // Prepare - the distinguishing behaviour against a squish: a killed Goomba moves with
        // no ground alignment, so the corpse sinks through the platform it was walking on. Walk first, so
        // it is genuinely resting on that floor when the blow lands.
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.motionUpdate();
        assertThat(goomba.isGrounded()).as("standing on the floor when struck").isTrue();
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Execute - long enough to clear the apex and sink past the floor row
        double deepest = goomba.getPosition().y();
        for (int tick = 0; tick < 80; tick++) {
            goomba.motionUpdate();
            deepest = max(deepest, goomba.getPosition().y());
        }

        // Verify - it went clean through the row it had been standing on
        assertThat(deepest)
            .as("sank below the floor it used to stand on")
            .isGreaterThan(FLOOR_ROW * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("It falls out of the bottom of the level still defeated, for its manager to retire for good")
    void killedGoombaFallsOutOfTheLevel() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Execute - it keeps falling until it is far below the level
        for (int tick = 0; tick < 400 && !goomba.hasFallenBelowTheLevel(); tick++) {
            goomba.motionUpdate();
        }

        // Verify - leaving the level is the manager's call (EnemyMotionManagerTest); the Goomba only has to
        // get there, and still count as dead when it does, so its placement is not released
        assertThat(goomba.hasFallenBelowTheLevel()).as("It fell clean out of the level").isTrue();
        assertThat(goomba.isDefeated()).as("Still a corpse, so it does not come back").isTrue();
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
            .spawn(eq(gameEngine), any(Coordinates.class));
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
        verify(tailAttackFlashMotionManager, never()).spawn(eq(gameEngine), any(Coordinates.class));
    }

    // -------------------------------------------------------------------------
    // The same death, dealt by a shell instead of a tail
    // -------------------------------------------------------------------------
    // Here rather than in a file of their own because they are assertions about this mechanism: a shell
    // kill IS the tail strike's death with two numbers changed. It puts the Goomba in the same killed
    // state, and the vertical flip comes from the shared dead-enemy motion rather than from either kill
    // site.

    @Test
    @DisplayName("A shell rolled or carried into the Goomba kills it the same way a tail does")
    void aKickedObjectKillsTheGoomba() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();

        // Execute - the knock arrives already signed, resolved by whatever struck it
        goomba.onStruckByKickedObject(SHELL_KNOCK_RIGHT);

        // Verify
        assertThat(goomba.isSquished()).as("not the stomp death").isFalse();
        assertThat(goomba.getWhamMotion()).as("thrown off the level instead").isNotNull();
        assertThat(goomba.isHittable()).as("intangible from the blow onwards").isFalse();
        assertThat(goomba.getPendingScoreReward())
            .as("the striker banks the 100: the award is made at the shell, not here")
            .isNull();
    }

    @Test
    @DisplayName("The shell kill throws it lower than a tail strike, and in the knock's own direction")
    void theShellKillThrowsItLowerThanATailStrike() {
        // -48 against the tail's -80, which is the one visible difference between the two deaths.
        // Prepare
        final Goomba shellKilled = goombaAt(5, WALK_ROW);
        final Goomba tailKilled = goombaAt(5, WALK_ROW);
        shellKilled.onStruckByKickedObject(-SHELL_KNOCK_RIGHT);
        tailKilled.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));

        // Execute - past the one-tick pose, onto the frame the launch is written
        shellKilled.motionUpdate();
        shellKilled.motionUpdate();
        tailKilled.motionUpdate();
        tailKilled.motionUpdate();

        // Verify
        assertThat(shellKilled.getWhamMotion().getKnockLaunchVelocity().y())
            .as("-48")
            .isEqualTo(SHELL_KILL_LAUNCH_Y);
        assertThat(tailKilled.getWhamMotion().getKnockLaunchVelocity().y())
            .as("-80, the deeper throw")
            .isLessThan(SHELL_KILL_LAUNCH_Y);
        assertThat(shellKilled.getPosition().x())
            .as("knocked leftward, as the negative knock asked")
            .isLessThan(5 * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("A Goomba already dying cannot be killed again by a shell")
    void aDyingGoombaIsNotKilledTwice() {
        // Object-to-object detection is off for the killed and squashed states, so neither death can be
        // restarted.
        // Prepare
        final Goomba tailKilled = goombaAt(5, WALK_ROW);
        tailKilled.onTailAttack(playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT));
        final EnemyKnockMotion firstDeath = tailKilled.getWhamMotion();
        final Goomba squished = goombaAt(5, WALK_ROW);
        squished.onCollisionFromAbove(playerAt(5 * TILE_SPRITE_SIZE, 0, RIGHT));

        // Execute
        tailKilled.onStruckByKickedObject(SHELL_KNOCK_RIGHT);
        squished.onStruckByKickedObject(SHELL_KNOCK_RIGHT);

        // Verify
        assertThat(tailKilled.getWhamMotion()).as("the first death stands").isSameAs(firstDeath);
        assertThat(squished.getWhamMotion()).as("a flattened Goomba is not thrown").isNull();
        assertThat(squished.isSquished()).isTrue();
    }

    // -------------------------------------------------------------------------
    // The player's kill chain, which is what makes consecutive stomps escalate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Two Goombas stomped on one jump pay 100 then 200")
    void consecutiveStompsEscalate() {
        // The kill tally is read before it is incremented, so the first kill of a chain pays index 0 - a
        // flat 100 - and the second pays 200. This is the player's own chain, the counterpart of the
        // per-shell kill tally a rolling shell carries.
        // Prepare - one player, two Goombas, no ground contact in between
        final LevelScenePlayer player = descendingPlayer();
        final Goomba first = goombaAt(5, WALK_ROW);
        final Goomba second = goombaAt(7, WALK_ROW);

        // Execute
        first.onCollisionFromAbove(player);
        second.onCollisionFromAbove(player);

        // Verify
        assertThat(first.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(second.getPendingScoreReward()).isEqualTo(SCORE_200);
    }

    @Test
    @DisplayName("Touching the ground between two stomps restarts the chain at 100")
    void landingBetweenStompsRestartsTheChain() {
        // The kill tally counts the successful hits made WITHOUT touching the ground, and every ground
        // contact zeroes it along with the airborne flag - which is PlayerRuntimeState.stop() here. So chain
        // scoring is a per-jump mechanic, not a running total.
        // Prepare
        final LevelScenePlayer player = descendingPlayer();
        final Goomba first = goombaAt(5, WALK_ROW);
        final Goomba second = goombaAt(7, WALK_ROW);

        // Execute
        first.onCollisionFromAbove(player);
        player.getRuntimeState().stop();
        second.onCollisionFromAbove(player);

        // Verify
        assertThat(first.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(second.getPendingScoreReward()).as("back to the bottom of the ladder").isEqualTo(SCORE_100);
    }

    @Test
    @DisplayName("A long enough chain tops out at an extra life and stays there")
    void aLongChainTopsOutAtAnExtraLife() {
        // Two caps: the kill tally itself is held at 8, and the resulting score index is then clamped at
        // 13, the 1-up.
        // Prepare
        final LevelScenePlayer player = descendingPlayer();

        // Execute - well past the ladder's nine rungs
        final List<Reward> awards = new ArrayList<>();
        for (int kill = 0; kill < 12; kill++) {
            final Goomba goomba = goombaAt(5, WALK_ROW);
            goomba.onCollisionFromAbove(player);
            awards.add(goomba.getPendingScoreReward());
        }

        // Verify
        assertThat(awards.subList(0, 4)).containsExactly(SCORE_100, SCORE_200, SCORE_400, SCORE_800);
        assertThat(awards.subList(8, 12)).as("the 1-up, repeating").containsOnly(ONE_UP);
    }

    @Test
    @DisplayName("A tail strike is paid at the chain's current height but does not advance it")
    void aTailStrikeReadsTheChainWithoutAdvancingIt() {
        // A tail strike reads the kill tally and never increments it, unlike every stomp.
        // Prepare - one stomp to lift the chain off the bottom rung
        final LevelScenePlayer player = playerAt(4 * TILE_SPRITE_SIZE, WALK_ROW_PIXEL_Y, RIGHT);
        goombaAt(5, WALK_ROW).onCollisionFromAbove(player);
        final Goomba firstWhacked = goombaAt(6, WALK_ROW);
        final Goomba secondWhacked = goombaAt(7, WALK_ROW);

        // Execute
        firstWhacked.onTailAttack(player);
        secondWhacked.onTailAttack(player);

        // Verify
        assertThat(firstWhacked.getPendingScoreReward()).as("the chain stood at one").isEqualTo(SCORE_200);
        assertThat(secondWhacked.getPendingScoreReward()).as("and still does").isEqualTo(SCORE_200);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** @return the single point the flash was spawned at */
    private Coordinates captureFlashOffset() {
        final ArgumentCaptor<Coordinates> offsetCaptor = ArgumentCaptor.forClass(Coordinates.class);
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
        final Goomba goomba = new Goomba(gameEngine, of(column, row));
        goomba.init();
        return goomba;
    }

    /** A player falling onto a Goomba, with a live kill chain of its own. */
    private static LevelScenePlayer descendingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setDY(2.0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        return player;
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
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        doCallRealMethod().when(player).currentKillChain();
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
