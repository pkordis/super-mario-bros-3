package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaFamilyEnemy;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_200;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_400;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_800;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_8000;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KICKED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.KICK_HIT_COOLDOWN_TICKS;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.KICK_X_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy.BODY_SIZE_PIXELS;
import static house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy.STOMP_HIT_COOLDOWN_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kicking a shell, and what the shell then is: {@code Player_KickObject} (dasm prg000 @ PRG000_CE2F)
 * and {@code ObjState_Kicked} (@ PRG000_CC6E).
 *
 * <p>The three ways in all land in one routine, which is why there is no separate kickable type: B
 * released out of the hands, a body contact with B <em>not</em> held, and a stomp. The last of them is
 * the odd one out and deliberately so — it scores and kicks, and shows no kick frame, because the ROM
 * zeroes {@code Player_Kick} the instant it returns.
 *
 * <p>Whether the release and the contact reach this at all is {@code KoopaTroopaCarryTest}'s subject;
 * what happens once they do is this one's.
 */
class KoopaTroopaKickTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 24;
    private static final int ROWS = 10;
    private static final int WALK_FRAME_COUNT = 2;

    private static final int FLOOR_ROW = 6;
    private static final int SHELL_ROW = FLOOR_ROW - 1;
    private static final int SHELL_COLUMN = 8;

    /** Resting feet line for a one-tile-tall body standing on {@link #FLOOR_ROW}. */
    private static final double RESTING_Y = SHELL_ROW * (double) TILE_SPRITE_SIZE;
    private static final double SHELL_X = SHELL_COLUMN * (double) TILE_SPRITE_SIZE;

    /** The player's sprite is two tiles tall and its feet are always at {@code Y + 32}. */
    private static final double PLAYER_STANDING_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE - 2 * TILE_SPRITE_SIZE;

    // Shell physics constants, qualified rather than statically imported: four of their names run past
    // the 120-column limit as import lines.
    private static final int CHAIN_KNOCK = KoopaTroopaFamilyEnemy.SHELL_CHAIN_KNOCK_X_VELOCITY_FIXED_POINT;
    private static final int KILL_LAUNCH_Y = KoopaTroopaFamilyEnemy.SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT;
    private static final int ROLL_REDROP_Y = KoopaTroopaFamilyEnemy.SHELL_ROLL_RE_DROP_Y_VELOCITY_FIXED_POINT;
    private static final int ROLL_X = KoopaTroopaFamilyEnemy.SHELL_ROLL_X_VELOCITY_FIXED_POINT;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private GameEngine gameEngine;
    private boolean[][] solids;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }

        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(collisionGrid.isBlockBumpActiveAt(any())).thenReturn(false);

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        final KoopaTroopaAnimator animator = mock(KoopaTroopaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaTroopaAnimator.class))
            .thenReturn(animator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TailAttackFlashMotionManager.class))
            .thenReturn(mock(TailAttackFlashMotionManager.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    // -------------------------------------------------------------------------
    // The kick itself
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A standing kick sends the shell at exactly 3 px/frame, in the player's facing")
    void aStandingKickUsesTheBaseSpeed() {
        // ObjectKickXVelMoving = {-$30, $30} (dasm prg000.asm:2794).
        // Prepare
        final KoopaTroopa troopa = restingShell();
        final LevelScenePlayer player = playerAt(SHELL_X - 40, RIGHT, 0);

        // Execute
        troopa.onKicked(player, true);

        // Verify
        assertThat(troopa.getState()).isEqualTo(KICKED);
        assertThat(troopa.getVelocity().x()).isEqualTo(KICK_X_VELOCITY_FIXED_POINT);
        assertThat(troopa.getVelocity().pixelStepX()).as("3.0 px/frame").isCloseTo(3.0, within(TOLERANCE));
        assertThat(troopa.getVelocity().y()).as("YVel is zeroed by the kick").isZero();
    }

    @Test
    @DisplayName("A running kick adds half the player's own speed, so it is measurably faster")
    void aRunningKickAddsHalfThePlayersSpeed() {
        // dasm prg000.asm:2961-2969: EOR against Player_XVel to test the sign, then an arithmetic shift
        // right before the add.
        // Prepare - a player running right at 2.5 px/frame, which is $28 in 4.4 fixed point
        final KoopaTroopa standing = restingShell();
        final KoopaTroopa running = restingShell();

        // Execute
        standing.onKicked(playerAt(SHELL_X - 40, RIGHT, 0), true);
        running.onKicked(playerAt(SHELL_X - 40, RIGHT, 2.5), true);

        // Verify
        assertThat(running.getVelocity().x())
            .as("$30 + $28/2")
            .isEqualTo(KICK_X_VELOCITY_FIXED_POINT + 0x28 / 2);
        assertThat(running.getVelocity().x())
            .as("a running kick genuinely outruns a standing one")
            .isGreaterThan(standing.getVelocity().x());
    }

    @Test
    @DisplayName("Running the other way adds nothing: the bonus needs a shared direction")
    void aKickAgainstThePlayersMotionGetsNoBonus() {
        // Prepare - running right, kicking left
        final KoopaTroopa troopa = restingShell();

        // Execute
        troopa.onKicked(playerAt(SHELL_X + 40, LEFT, 2.5), false);

        // Verify
        assertThat(troopa.getVelocity().x()).isEqualTo(-KICK_X_VELOCITY_FIXED_POINT);
    }

    @Test
    @DisplayName("The halving preserves the sign, as the ROM's arithmetic shift does")
    void theMomentumBonusIsSignPreserving() {
        // ASL/ROR floors toward negative infinity rather than toward zero; >> on an int does the same.
        // Prepare - running left at an odd fixed-point speed ($29 = 2.5625 px/frame)
        final KoopaTroopa troopa = restingShell();

        // Execute
        troopa.onKicked(playerAt(SHELL_X + 40, LEFT, -0x29 / 16.0), false);

        // Verify
        assertThat(troopa.getVelocity().x()).isEqualTo(-KICK_X_VELOCITY_FIXED_POINT + (-0x29 >> 1));
    }

    @Test
    @DisplayName("Every kick arms the 16-tick window in which the shell cannot hurt the player")
    void theKickArmsTheHitCooldown() {
        // Objects_Timer2 = $10 (dasm prg000.asm:2838-2840). Without it the shell you just kicked is
        // still inside your hitbox and would kill you on the spot.
        // Prepare
        final KoopaTroopa troopa = restingShell();
        final LevelScenePlayer player = playerAt(SHELL_X - 40, RIGHT, 0);

        // Execute
        troopa.onKicked(player, true);

        // Verify
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(KICK_HIT_COOLDOWN_TICKS);
        assertThat(troopa.canHurtPlayer()).as("harmless while the window runs").isFalse();

        // Execute - run the window out
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);

        // Verify
        assertThat(troopa.getHitCooldownTicks()).isZero();
        assertThat(troopa.canHurtPlayer()).as("and lethal the moment it expires").isTrue();
    }

    @Test
    @DisplayName("Every fresh kick restarts the kill chain from 100 points")
    void everyFreshKickResetsTheTally() {
        // Objects_KillTally = 0 (dasm prg000.asm:2884-2886).
        // Prepare - a shell that has already bowled through two enemies
        final KoopaTroopa troopa = kickedShell(true);
        troopa.onKickedInto(killableObject());
        troopa.onKickedInto(killableObject());
        assertThat(troopa.getKillTally()).isEqualTo(2);

        // Execute
        troopa.onKicked(playerAt(SHELL_X - 40, RIGHT, 0), true);

        // Verify
        assertThat(troopa.getKillTally()).isZero();
        troopa.setPendingScoreReward(null);
        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).as("back to the bottom of the ladder").isEqualTo(SCORE_100);
    }

    // -------------------------------------------------------------------------
    // Stomping a shell
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Stomping a resting shell kicks it and scores, and shows no kick frame")
    void stompingAShellKicksItWithoutThePose() {
        // PRG000_D2A2: Kill_Tally++, Score_Get100PlusPts, Player_KickObject, then Player_Kick = 0. The
        // zeroing is the whole point - funnel this through the pose-arming helper and a stomp would draw
        // the kick frame it never draws in the original.
        // Prepare
        final KoopaTroopa troopa = restingShell();
        final LevelScenePlayer player = playerAt(SHELL_X - 8, RIGHT, 0);
        player.getPosition().setDY(2.0);

        // Execute
        troopa.onCollisionFromAbove(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(KICKED);
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(player.getRuntimeState().isKicking()).as("no kick pose for a stomp").isFalse();
        assertThat(player.getRuntimeState().getKickCountdown()).isZero();
    }

    @Test
    @DisplayName("Stomping a shell does not bounce the player, unlike every other stomp")
    void stompingAShellDoesNotBounceThePlayer() {
        // PRG000_D2A2 returns before reaching PRG000_D2B4's Player_YVel = -$40, so the player lands on
        // the shell and stays there while it shoots away.
        // Prepare
        final KoopaTroopa troopa = restingShell();
        final LevelScenePlayer player = playerAt(SHELL_X - 8, RIGHT, 0);
        player.getPosition().setDY(2.0);

        // Execute
        troopa.onCollisionFromAbove(player);

        // Verify
        assertThat(player.getPosition().getDY()).as("still descending, unbounced").isEqualTo(2.0);
    }

    @Test
    @DisplayName("Stomping a rolling shell stops it dead rather than killing it")
    void stompingARollingShellStopsIt() {
        // A kicked shell takes PRG000_D2B4's ordinary stomp, which for a troopa ends in OA3_DIESHELLED
        // and so back in OBJSTATE_SHELLED.
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);
        final LevelScenePlayer player = playerAt(troopa.getPosition().x() - 8, RIGHT, 0);
        player.getPosition().setDY(2.0);

        // Execute
        troopa.onCollisionFromAbove(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.getVelocity().x()).isZero();
        assertThat(player.getPosition().getDY()).as("an ordinary stomp, so the player does bounce").isNegative();
    }

    // -------------------------------------------------------------------------
    // The roll
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A rolling shell is re-dropped onto the floor every landing rather than gliding along it")
    void theRollingShellIsRedroppedEveryLanding() {
        // dasm prg000.asm:2504-2507: Object_HitGround aligns it, then YVel = $0C pushes it straight back
        // off. The write lands AFTER Object_Move's gravity, so the velocity is exactly $0C and not
        // $0C plus a tick of gravity.
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);

        // Execute - long enough to land and settle into the cycle
        tick(troopa, 10);

        // Verify
        assertThat(troopa.getPosition().y())
            .as("riding the floor line")
            .isCloseTo(RESTING_Y, within(TOLERANCE));
        assertThat(troopa.getVelocity().y())
            .as("and pushed off it again, which is the re-drop")
            .isEqualTo(ROLL_REDROP_Y);
    }

    @Test
    @DisplayName("A rolling shell covers 3 px a tick along the floor")
    void theRollingShellKeepsItsSpeed() {
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, 4);
        final double startX = troopa.getPosition().x();

        // Execute
        tick(troopa, 10);

        // Verify
        assertThat(troopa.getPosition().x() - startX)
            .as("ten ticks at the base kick speed")
            .isCloseTo(10 * 3.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A landing with no horizontal speed re-launches the shell at 1.5 px/frame, towards the player")
    void aStalledRollRelaunchesTowardsThePlayer() {
        // ObjKickXvel = {$18, -$18} indexed by Level_ObjCalcXDiffs (dasm prg000.asm:2478, :2495-2499),
        // whose index is 0 when the player is to the RIGHT - so it pairs with +$18 and the shell comes
        // BACK at you. Exercised directly: no path in this port yet produces a kicked object with zero
        // horizontal speed (the ROM's guard covers the Ice Block and the Bob-omb, both out of scope), so
        // there is nothing to drive it through a tick with.
        // Prepare
        final KoopaTroopa troopa = restingShell();
        final LevelScenePlayer onTheRight = playerAt(SHELL_X + 40, LEFT, 0);
        final LevelScenePlayer onTheLeft = playerAt(SHELL_X - 40, RIGHT, 0);

        // Execute & Verify - player to the right
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(onTheRight));
        assertThat(troopa.rollLaunchXVelocity()).isEqualTo(ROLL_X);

        // Execute & Verify - player to the left
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(onTheLeft));
        assertThat(troopa.rollLaunchXVelocity()).isEqualTo(-ROLL_X);
        assertThat(ROLL_X / (double) TILE_SPRITE_SIZE)
            .as("1.5 px/frame")
            .isCloseTo(1.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("A rolling shell reverses at a wall and bumps whatever block was there")
    void theRollingShellReversesAtAWallAndBumpsTheBlock() {
        // Object_BumpOffBlocks on wall contact (dasm prg000.asm:2558-2560), then Object_AboutFace. The
        // bump is routed through the terrain tail-strike probe because that is literally what
        // Object_BumpOffBlocks' own header says it does.
        // Prepare - a wall two columns along, and a player for the block to award to
        solidColumn(SHELL_COLUMN + 2);
        final LevelScenePlayer blockOwner = playerAt(SHELL_X - 40, RIGHT, 0);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(blockOwner));
        final KoopaTroopa troopa = kickedShell(true);

        // Execute
        tick(troopa, 20);

        // Verify
        assertThat(troopa.getVelocity().x()).as("heading back the way it came").isNegative();
        verify(collisionGrid, times(1)).resolveTailAttack(any(), anyDouble(), anyDouble());
        assertThat(troopa.getPosition().x())
            .as("never inside the wall")
            .isLessThan((SHELL_COLUMN + 2) * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("A shell-triggered block does not bounce the player, which is Player_BounceObj's whole job")
    void aShellTriggeredBlockLeavesThePlayerAlone() {
        // Player_BounceObj exists because a bumped block spawns a bounce OBJECT whose init pushes
        // Player_YVel down, and the flag suppresses that when an object caused the bump (dasm
        // prg001.asm:978-986). Nothing on this project's block-bump path writes player velocity at all,
        // so the equivalent holds by construction - pinned here so it stays that way.
        // Prepare
        solidColumn(SHELL_COLUMN + 2);
        final LevelScenePlayer blockOwner = playerAt(SHELL_X - 40, RIGHT, 0);
        blockOwner.getPosition().setDY(0);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(blockOwner));
        final KoopaTroopa troopa = kickedShell(true);

        // Execute
        tick(troopa, 20);

        // Verify
        assertThat(blockOwner.getPosition().getDY()).as("untouched by the bump the shell caused").isZero();
        verify(collisionGrid, times(1)).resolveTailAttack(any(), anyDouble(), anyDouble());
    }

    // -------------------------------------------------------------------------
    // Chained kills
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A rolling shell kills what it hits, knocking it along in its own direction of travel")
    void theRollingShellKnocksAndKillsWhatItHits() {
        // dasm prg000.asm:2600-2607: the struck object takes ±$10 in the SHELL's direction, then
        // ObjectKill_SetShellKillVars.
        // Prepare
        final KoopaTroopa rightward = kickedShell(true);
        final KoopaTroopa leftward = kickedShell(false);
        final ActiveLevelObject struckByRight = killableObject();
        final ActiveLevelObject struckByLeft = killableObject();

        // Execute
        rightward.onKickedInto(struckByRight);
        leftward.onKickedInto(struckByLeft);

        // Verify
        verify(struckByRight).onStruckByKickedObject(CHAIN_KNOCK);
        verify(struckByLeft).onStruckByKickedObject(-CHAIN_KNOCK);
    }

    @Test
    @DisplayName("Each link of the chain pays more than the last, up the ROM's ladder")
    void theChainScoreEscalatesPerLink() {
        // Score_Get100PlusPts adds 5 to the tally and indexes ScoreAdd (dasm prg000.asm:699,
        // prg007.asm:2122), which is 100/200/400/800/1000/... - doubling except at the 1000 step.
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);

        // Execute & Verify
        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_100);

        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_200);

        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_400);

        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_800);
        assertThat(troopa.getKillTally()).isEqualTo(4);
    }

    @Test
    @DisplayName("A long enough chain tops out at an extra life and stays there")
    void theChainCapsAtAnExtraLife() {
        // Kill_Tally is held at 8 (prg007.asm:2155-2161) and the score index is clamped at $0D, the 1-up
        // (prg007.asm:2174-2178).
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);

        // Execute - eight links takes the tally to the top
        for (int link = 0; link < 8; link++) {
            troopa.onKickedInto(killableObject());
        }

        // Verify
        assertThat(troopa.getPendingScoreReward()).as("the eighth link pays 8000").isEqualTo(SCORE_8000);

        // Execute - and everything past it
        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).isEqualTo(ONE_UP);
        troopa.onKickedInto(killableObject());
        assertThat(troopa.getPendingScoreReward()).as("and stays there").isEqualTo(ONE_UP);
    }

    @Test
    @DisplayName("Two rolling shells meeting kill each other, each paying out its own tally")
    void twoRollingShellsKillEachOther() {
        // dasm prg000.asm:2610-2627. Both end in OBJSTATE_KILLED with the upward death throw, and the
        // one being struck pays the chain IT had built up rather than the striker's.
        // Prepare - one shell with two kills behind it, meeting a fresh one head-on
        final KoopaTroopa veteran = kickedShell(true);
        veteran.onKickedInto(killableObject());
        veteran.onKickedInto(killableObject());
        veteran.setPendingScoreReward(null);
        final KoopaTroopa fresh = kickedShell(false);

        // Execute
        veteran.onKickedInto(fresh);

        // Verify
        assertThat(veteran.getState()).isEqualTo(KILLED);
        assertThat(fresh.getState()).isEqualTo(KILLED);
        assertThat(veteran.getPendingScoreReward()).as("two links already banked, so the third").isEqualTo(SCORE_400);
        assertThat(fresh.getPendingScoreReward()).as("a chain of none").isEqualTo(SCORE_100);
        assertThat(veteran.getVelocity().y()).isEqualTo(KILL_LAUNCH_Y);
        assertThat(fresh.getVelocity().y()).isEqualTo(KILL_LAUNCH_Y);
    }

    @Test
    @DisplayName("A killed shell drops out of the broadphase, so it kills nothing further")
    void aKilledShellIsNoLongerAHazard() {
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);

        // Execute
        troopa.onStruckByKickedObject(CHAIN_KNOCK);

        // Verify
        assertThat(troopa.isKickedHazard()).isFalse();
        assertThat(troopa.isHittable()).isFalse();

        // Execute - and a second strike changes nothing
        final ActiveLevelObject bystander = killableObject();
        troopa.onKickedInto(bystander);
        verify(bystander, never()).onStruckByKickedObject(anyInt());
    }

    // -------------------------------------------------------------------------
    // Hurting the player
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A rolling shell hurts the player on contact once its cooldown has run out")
    void theRollingShellHurtsThePlayer() {
        // Prepare - the player standing still, which skips the exemption's first test outright
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);
        final LevelScenePlayer player = playerAt(troopa.getPosition().x() - 8, RIGHT, 0);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        verify(player).getHurtBy(troopa);
    }

    @Test
    @DisplayName("Chasing your own shell is safe: no hurt while it is ahead of you and moving your way")
    void chasingYourOwnShellIsSafe() {
        // dasm prg000.asm:4080-4103. Same X-velocity sign AND the shell ahead of the player in that
        // direction, and the hurt is skipped.
        // Prepare - a shell rolling right, with the player behind it also running right
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);
        final LevelScenePlayer player = playerAt(troopa.getPosition().x() - 8, RIGHT, 2.5);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        verify(player, never()).getHurtBy(any());
    }

    @Test
    @DisplayName("Being run down from behind is not: the shell behind you still hurts")
    void aShellCatchingUpFromBehindStillHurts() {
        // Prepare - a shell rolling right, with the player AHEAD of it also running right
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);
        final LevelScenePlayer player = playerAt(troopa.getPosition().x() + 8, RIGHT, 2.5);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        verify(player).getHurtBy(troopa);
    }

    @Test
    @DisplayName("Running into a shell head-on always hurts, whichever side it is on")
    void runningIntoAShellHeadOnHurts() {
        // The sign test comes first: opposite directions never reach the side comparison at all.
        // Prepare - a shell rolling right, the player running left into it
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);
        final LevelScenePlayer player = playerAt(troopa.getPosition().x() + 8, LEFT, -2.5);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        verify(player).getHurtBy(troopa);
    }

    @Test
    @DisplayName("A resting shell is still harmless, and still cannot be stomped into hurting you")
    void aRestingShellStaysHarmless() {
        // Prepare
        final KoopaTroopa troopa = restingShell();
        final LevelScenePlayer player = playerAt(SHELL_X - 8, RIGHT, 0);

        // Execute & Verify
        assertThat(troopa.canHurtPlayer()).isFalse();
        troopa.hurt(player);
        verify(player, never()).getHurtBy(any());
    }

    // -------------------------------------------------------------------------
    // The roll's sprite cycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The roll cycle advances one beat every two ticks and wraps after four")
    void theSpinCycleAdvancesEveryTwoTicks() {
        // (Level_NoStopCnt >> 1) & 3 (dasm prg000.asm:2653-2656): four beats, two ticks each.
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);
        final int[] beatsByTick = new int[9];

        // Execute & Verify
        for (int tick = 0; tick < beatsByTick.length; tick++) {
            beatsByTick[tick] = troopa.getSpinBeat();
            troopa.motionUpdate();
        }

        assertThat(beatsByTick).containsExactly(0, 0, 1, 1, 2, 2, 3, 3, 0);
    }

    @Test
    @DisplayName("No beat of the roll is mirrored, and the roll ignores the facing entirely")
    void noBeatOfTheRollIsMirrored() {
        // AND #~SPR_HFLIP / ORA ObjShell_AnimFlipBits,Y (dasm prg000.asm:2657-2660): the roll CLEARS the
        // facing's flip and rewrites it from the beat table, so a shell going left and one going right
        // are drawn identically. What the table then asks for - beat 4 mirroring beat 2 - is shipped as
        // art (koopa_troopa_shell_spin_3.png), so the beat picks a texture and nothing picks a flip.
        // Prepare
        final KoopaTroopa rightward = kickedShell(true);
        final KoopaTroopa leftward = kickedShell(false);

        // Execute & Verify
        for (int beat = 0; beat < 4; beat++) {
            assertThat(rightward.isSpriteMirrored())
                .as("beat %d, rolling right", beat)
                .isFalse();
            assertThat(leftward.isSpriteMirrored())
                .as("beat %d, rolling left - the same answer", beat)
                .isFalse();
            rightward.motionUpdate();
            rightward.motionUpdate();
            leftward.motionUpdate();
            leftward.motionUpdate();
        }
    }

    @Test
    @DisplayName("A rolling shell is drawn over its own column on every beat, with no width correction")
    void theMirrorCompensationFollowsTheNetFlip() {
        // The 16 px misplacement this pairing exists to prevent: a negative X scale swings the quad a
        // full width left of its anchor, so the compensation has to be applied on exactly the beats the
        // scale is negative - and on no others. For a shell that is no beat at all, including the ones a
        // right-facing troopa would be mirrored on were it still walking.
        // Prepare
        final KoopaTroopa troopa = kickedShell(true);
        final WorldOffset anchor = WorldOffset.of(5f, 3f, 0f);

        // Execute & Verify
        for (int beat = 0; beat < 4; beat++) {
            assertThat(troopa.isSpriteMirrored()).as("beat %d carries no flip", beat).isFalse();
            assertThat(troopa.shellDrawOffset(anchor).x())
                .as("beat %d needs no width added back", beat)
                .isEqualTo(anchor.x());
            troopa.motionUpdate();
            troopa.motionUpdate();
        }
    }

    @Test
    @DisplayName("Re-shelling a rolling troopa restarts the roll cycle at its first beat")
    void reShellingRestartsTheRollCycle() {
        // Prepare - a shell parked three beats into the cycle
        final KoopaTroopa troopa = kickedShell(true);
        tick(troopa, 6);
        assertThat(troopa.getSpinBeat()).as("mid-cycle before the stomp").isEqualTo(3);

        // Execute - stomped, which re-shells it
        final LevelScenePlayer player = playerAt(troopa.getPosition().x() - 8, RIGHT, 0);
        player.getPosition().setDY(2.0);
        tick(troopa, KICK_HIT_COOLDOWN_TICKS);
        troopa.onCollisionFromAbove(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.isSpriteMirrored()).as("a shell is never mirrored").isFalse();
        assertThat(troopa.getSpinBeat())
            .as("the next kick rolls from the resting frame, not from wherever this one stopped")
            .isZero();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private KoopaTroopa troopaAt(final int column, final int row) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(column, row));
        troopa.init();
        return troopa;
    }

    /** A shell resting on the floor, past its post-stomp hit cooldown. */
    private KoopaTroopa restingShell() {
        final KoopaTroopa troopa = troopaAt(SHELL_COLUMN, SHELL_ROW);
        final PlayerPosition stomper = new PlayerPosition();
        stomper.setDY(2.0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(stomper);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        troopa.onCollisionFromAbove(player);
        tick(troopa, STOMP_HIT_COOLDOWN_TICKS);
        troopa.setPendingScoreReward(null);
        return troopa;
    }

    /** A shell already rolling, kicked by a standing player from whichever side sends it that way. */
    private KoopaTroopa kickedShell(final boolean toTheRight) {
        final KoopaTroopa troopa = restingShell();
        final double playerX = toTheRight ? SHELL_X - 40 : SHELL_X + 40;
        troopa.onKicked(playerAt(playerX, toTheRight ? RIGHT : LEFT, 0), toTheRight);
        troopa.setPendingScoreReward(null);
        return troopa;
    }

    /** Something a rolling shell can kill, which records the knock it was given. */
    private static ActiveLevelObject killableObject() {
        final ActiveLevelObject other = mock(ActiveLevelObject.class);
        when(other.getBounds()).thenReturn(AxisAlignedBoundingBox.ofSize(
            SHELL_X,
            RESTING_Y,
            new DimensionsPixels(BODY_SIZE_PIXELS, BODY_SIZE_PIXELS)
        ));
        when(other.getVelocity()).thenReturn(FixedPointVelocity.STILL);
        when(other.isKickedHazard()).thenReturn(false);
        return other;
    }

    /**
     * A player at the given X, facing the given way and moving at the given pixel speed. The hurt
     * exemption reads all three, so none of them can be left to a default.
     */
    private static LevelScenePlayer playerAt(
        final double positionX,
        final PlayerOrientationHorizontal facing,
        final double dx
    ) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(positionX);
        position.setY(PLAYER_STANDING_Y);
        position.setDX(dx);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        doCallRealMethod().when(player).currentKillChain();
        when(player.getOrientation()).thenReturn(new PlayerOrientation(facing, SUSTAINED));
        when(player.getVisibility()).thenReturn(FOREGROUND);        when(player.isSmall()).thenReturn(false);
        when(player.asLevelScenePlayer()).thenReturn(player);
        doCallRealMethod().when(player).kickOnContact(any());
        doCallRealMethod().when(player).kickWithoutPose(any());
        return player;
    }

    private void tick(final KoopaTroopa troopa, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            troopa.motionUpdate();
        }
    }

    private void solidColumn(final int column) {
        for (int row = 0; row < ROWS; row++) {
            solids[row][column] = true;
        }
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
