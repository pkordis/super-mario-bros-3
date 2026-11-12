package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_Y_VELOCITY;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.SoundEffect.ENEMY_STOMP;
import static house.x1337.app.smb3.enumeration.SoundEffect.KOOPA_SHELL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy.STOMP_HIT_COOLDOWN_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaFamilyEnemy.SHELL_FEET_THRESHOLD_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaFamilyEnemy.SHELL_SHAKE_THRESHOLD_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaFamilyEnemy.TAIL_STRIKE_HIT_COOLDOWN_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaFamilyEnemy.WAKE_UP_TICKS;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Koopa Troopa as a collision participant: every defeat path lands it in the shell, the shell is
 * simulated rather than parked, it shakes itself awake and stands back up.
 *
 * <p>The through-line worth keeping in mind while reading: a troopa goes into its shell when defeated and is
 * never squashed, which means <b>nothing here kills the troopa</b>. A stomp shells
 * it, a tail wham shells it, a block bumped underneath shells it, and a tail wham on the shell shells it
 * again. Only a kick into a wall could remove it, and that is not in this slice.
 */
class KoopaTroopaShellTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 24;
    private static final int ROWS = 10;
    private static final int WALK_FRAME_COUNT = 2;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

    /** The feet mask table's rows are 16 timer units wide, which is what {@code wakeUpTicks >> 4} indexes. */
    private static final int FEET_FLICKER_BAND_TICKS = 16;

    /**
     * A second, deeper floor used by the apex test, which needs the launch's full {@code 69} px of rise
     * to fit inside the level. A troopa resting on it tracks {@code y = 128}, so the arc peaks at
     * {@code y = 58.8}: head in row 3, feet ten pixels into row 4.
     */
    private static final int DEEP_FLOOR_ROW = 9;
    private static final int APEX_FEET_ROW = 4;

    /** {@code 27} frames of rise carries the shell 13 px right of column 5, so its centre reads 6. */
    private static final int APEX_COLUMN = 6;

    /** {@code -80} from a tail strike, {@code -48} from a block bump, {@code ±8} either way. */
    private static final int TAIL_LAUNCH_Y_VELOCITY = -80;
    private static final int BUMP_LAUNCH_Y_VELOCITY = -48;
    private static final int KNOCK_X_VELOCITY = 8;

    /**
     * 415 frames to run the wake-up timer from {@code 255} to zero: 160 units at the central loop's
     * half rate while the timer is at or above {@code 96}, then 95 at full rate.
     */
    private static final int FRAMES_TO_EMPTY_THE_WAKE_UP_CLOCK = 415;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private TailAttackFlashMotionManager tailAttackFlashMotionManager;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private GameEngine gameEngine;
    private boolean[][] solids;
    private Set<Coordinates> oneWayPlatforms;
    private Set<Coordinates> bumpedBlockCells;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }
        oneWayPlatforms = new HashSet<>();
        bumpedBlockCells = new HashSet<>();

        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(any())).thenAnswer(invocation -> {
            final Coordinates cell = invocation.getArgument(0);
            return isGroundSolid(cell.x(), cell.y());
        });
        when(collisionGrid.isWallOrCeilingSolidTile(any())).thenAnswer(invocation -> {
            final Coordinates cell = invocation.getArgument(0);
            return isSolid(cell.x(), cell.y());
        });
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(collisionGrid.isBlockBumpActiveAt(any()))
            .thenAnswer(invocation -> bumpedBlockCells.contains(invocation.<Coordinates>getArgument(0)));

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        final KoopaTroopaAnimator animator = mock(KoopaTroopaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        tailAttackFlashMotionManager = mock(TailAttackFlashMotionManager.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(SoundPlayer.class)).thenReturn(mock(SoundPlayer.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaTroopaAnimator.class))
            .thenReturn(animator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TailAttackFlashMotionManager.class))
            .thenReturn(tailAttackFlashMotionManager);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    // -------------------------------------------------------------------------
    // Hurting the player
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A walking troopa hurts the player on a body hit and on a hit from below")
    void aWalkingTroopaHurtsThePlayer() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final LevelScenePlayer player = playerAtPixelX(5 * TILE_SPRITE_SIZE);

        // Execute
        troopa.onPlayerOverlap(player);
        troopa.onCollisionFromBelow(player);

        // Verify
        verify(player, times(2)).getHurtBy(troopa);
    }

    @Test
    @DisplayName("A shelled troopa neither hurts the player nor can be stomped a second time")
    void aShelledTroopaIsInertToThePlayersBody() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        troopa.setPendingScoreReward(null);

        // Execute
        final LevelScenePlayer player = playerAtPixelX(5 * TILE_SPRITE_SIZE);
        troopa.onPlayerOverlap(player);
        troopa.onCollisionFromBelow(player);
        troopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        verify(player, never()).getHurtBy(any());
        assertThat(troopa.getPendingScoreReward())
            .as("A shelled troopa is routed away from the hurt branch")
            .isNull();
    }

    @Test
    @DisplayName("It stays in the broadphase even while shelled, because the tail must still reach it")
    void aShelledTroopaStaysHittable() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);

        // Execute
        troopa.onCollisionFromAbove(descendingPlayer());

        // Verify - tail hits stay enabled in the shelled state
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.isHittable()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Stomp
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A stomp shells the troopa rather than killing it, bounces the player and pays 100")
    void aStompShellsItAndPaysOnce() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final LevelScenePlayer player = descendingPlayer();

        // Execute
        troopa.onCollisionFromAbove(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.getWakeUpTicks())
            .as("Going into the shell starts the wake-up timer at 255")
            .isEqualTo(WAKE_UP_TICKS);
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(STOMP_HIT_COOLDOWN_TICKS);
        assertThat(troopa.isExpired()).as("It is shelled, not dead").isFalse();
        assertThat(troopa.isUpsideDown()).as("A stomp does not flip it").isFalse();
        assertThat(player.getPosition().getDY()).isCloseTo(PLAYER_STOMP_BOUNCE_Y_VELOCITY, within(TOLERANCE));
    }

    @Test
    @DisplayName("One stomp cannot register twice, so it never pays out twice")
    void oneStompRegistersOnce() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        troopa.setPendingScoreReward(null);

        // Execute - the same stomp arriving again on the next collision pass
        troopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        assertThat(troopa.getPendingScoreReward()).isNull();
    }

    @Test
    @DisplayName("A stomp plays the stomp sound, and the same stomp arriving again plays nothing more")
    void aStompPlaysTheStompSoundOnce() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final LevelScenePlayer player = descendingPlayer();

        // Execute
        troopa.onCollisionFromAbove(player);
        troopa.onCollisionFromAbove(player);

        // Verify
        verify(troopa.getSoundPlayer(), times(1)).play(ENEMY_STOMP);
    }

    @Test
    @DisplayName("A tail strike plays the kill sound once")
    void aTailStrikePlaysTheKillSound() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final LevelScenePlayer player = playerAtPixelX(2 * TILE_SPRITE_SIZE);

        // Execute
        troopa.onTailAttack(player);

        // Verify
        verify(troopa.getSoundPlayer(), times(1)).play(KOOPA_SHELL);
    }

    @Test
    @DisplayName("A block bumped under a walking troopa plays the kill sound once")
    void aBlockBumpPlaysTheKillSound() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();
        tick(troopa, 1);
        bumpTheBlockUnder(troopa);
        final SoundPlayer soundPlayer = StaticBeanFactory.getBean(SoundPlayer.class);

        // Execute
        tick(troopa, 1);

        // Verify
        verify(soundPlayer, times(1)).play(KOOPA_SHELL);
    }

    // -------------------------------------------------------------------------
    // Tail wham
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A tail strike whams the troopa up, away and upside down into the shell, for no score")
    void aTailStrikeWhamsItIntoTheShell() {
        // Prepare - the player is to the left, so the throw goes right
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final LevelScenePlayer player = playerAtPixelX(2 * TILE_SPRITE_SIZE);

        // Execute
        troopa.onTailAttack(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.getWakeUpTicks()).isEqualTo(WAKE_UP_TICKS);
        assertThat(troopa.isUpsideDown()).as("Flipped vertically on top of its facing").isTrue();
        assertThat(troopa.getVelocity().y())
            .as("The tail strike's launch is -80")
            .isEqualTo(TAIL_LAUNCH_Y_VELOCITY);
        assertThat(troopa.getVelocity().x())
            .as("The knock throws it away from the player")
            .isEqualTo(KNOCK_X_VELOCITY);
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(TAIL_STRIKE_HIT_COOLDOWN_TICKS);
        assertThat(troopa.getPendingScoreReward())
            .as("No score at all for an enemy that goes into its shell when defeated")
            .isNull();
        assertThat(troopa.isExpired()).isFalse();
        verify(tailAttackFlashMotionManager).spawn(any(), any());
    }

    @Test
    @DisplayName("The wham is thrown away from whichever side the player struck from")
    void theWhamIsDirectedAwayFromThePlayer() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);

        // Execute
        troopa.onTailAttack(playerAtPixelX(9 * TILE_SPRITE_SIZE));

        // Verify
        assertThat(troopa.getVelocity().x())
            .as("Player on the right throws it left")
            .isEqualTo(-KNOCK_X_VELOCITY);
    }

    @Test
    @DisplayName("A tail strike on an already-shelled troopa re-launches it and resets the wake-up clock")
    void aSecondTailStrikeResetsTheClockRatherThanKilling() {
        // Prepare - shelled, then left to run its clock down a long way. The stomp's 100 points are
        // cleared because the manager's postCollision would already have taken them.
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        troopa.setPendingScoreReward(null);
        tick(troopa, 200);
        assertThat(troopa.getWakeUpTicks()).isLessThan(WAKE_UP_TICKS);

        // Execute
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));

        // Verify - there is no second-hit kill; the tail alone can never remove a troopa
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.getWakeUpTicks()).isEqualTo(WAKE_UP_TICKS);
        assertThat(troopa.getVelocity().y()).isEqualTo(TAIL_LAUNCH_Y_VELOCITY);
        assertThat(troopa.isUpsideDown()).isTrue();
        assertThat(troopa.isExpired()).isFalse();
        assertThat(troopa.getPendingScoreReward()).isNull();
    }

    @Test
    @DisplayName("A whammed troopa arcs, lands with the damped bounce and settles on the floor")
    void theWhamSettlesInsteadOfFlyingAway() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));

        // Execute - one frame of the launch, then long enough to arc, land and stop bouncing
        tick(troopa, 1);
        final double apexY = troopa.getPosition().y();
        tick(troopa, 120);

        // Verify
        assertThat(apexY).as("It went up before it came down").isLessThan(WALK_ROW_PIXEL_Y);
        assertThat(troopa.isGrounded()).as("Come to rest").isTrue();
        assertThat(troopa.getPosition().y())
            .as("Back on the floor it was thrown off")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.getVelocity().x()).as("The halving ran the slide out").isZero();
        assertThat(troopa.isExpired()).as("A shell is never thrown out of the level by a tail").isFalse();
        assertThat(troopa.getState()).isEqualTo(SHELLED);
    }

    @Test
    @DisplayName("Every landing halves the shell's slide, so a thrown shell runs itself out")
    void eachLandingHalvesTheSlide() {
        // The X velocity is halved on every descending floor contact, sign preserved. A
        // shell thrown at 8 therefore slides to a stop over four landings rather than skating forever.
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));

        // Execute & Verify
        assertThat(tickUntilTheSlideChanges(troopa)).as("First landing").isEqualTo(4);
        assertThat(tickUntilTheSlideChanges(troopa)).as("Second landing").isEqualTo(2);
        assertThat(tickUntilTheSlideChanges(troopa)).as("Third landing").isEqualTo(1);
        assertThat(tickUntilTheSlideChanges(troopa)).as("Fourth landing stops it").isZero();
    }

    @Test
    @DisplayName("The first landing rebounds the shell off the floor before it settles there")
    void theLandingRebounds() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));

        // Execute - tick to the first landing, which is the frame the slide first halves
        tickUntilTheSlideChanges(troopa);

        // Verify - landing put it on the floor and the damped rebound took it straight back off
        assertThat(troopa.getPosition().y()).isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.isGrounded()).as("A quarter of the fall is given back as lift").isFalse();
        assertThat(troopa.getVelocity().y()).as("And that lift is upward").isNegative();

        // Execute
        tick(troopa, 120);

        // Verify - gravity is added after the move, so a resting object always carries one
        // frame of fall speed; grounded is the honest test, not a zero velocity
        assertThat(troopa.isGrounded()).as("The hops die out").isTrue();
        assertThat(troopa.getPosition().y()).isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
    }

    @Test
    @DisplayName("A rolling shell reverses at a wall instead of walking through it")
    void aShellAboutFacesAtAWall() {
        // Prepare - a wall two columns to the troopa's right, and a wham that throws it that way
        solidColumn(7);
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));
        assertThat(troopa.getVelocity().x()).isEqualTo(KNOCK_X_VELOCITY);

        // Execute
        tick(troopa, 60);

        // Verify
        assertThat(troopa.getPosition().x())
            .as("Never overlaps the wall column")
            .isLessThan(7 * (double) TILE_SPRITE_SIZE);
    }

    // -------------------------------------------------------------------------
    // The floor-detection depth limit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A surface is only detected from the top 4 px of its tile")
    void theFloorIsOnlyDetectedNearTheTopOfItsTile() {
        // An object does not detect a floor it is 4 pixels or more down into: the test is the tracked Y's
        // offset within its tile (Y & 15) against the limit. Four pixels is one frame of the maximum fall
        // speed, so this never rejects a genuine landing - only a
        // surface the enemy is already sunk into.
        // Prepare - the fixture's floor is row 6, so a body resting on it tracks y = 80
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);

        // Execute & Verify
        assertThat(troopa.isGroundBelow(new PixelPosition(80, 80)))
            .as("Flush with the surface")
            .isTrue();
        assertThat(troopa.isGroundBelow(new PixelPosition(80, 83)))
            .as("Three pixels in, which a terminal-velocity frame can produce")
            .isTrue();
        assertThat(troopa.isGroundBelow(new PixelPosition(80, 84)))
            .as("Four pixels in is already past the limit")
            .isFalse();
        assertThat(troopa.isGroundBelow(new PixelPosition(80, 90)))
            .as("Ten pixels in: sunk into the tile, not landing on it")
            .isFalse();
    }

    @Test
    @DisplayName("A shell sliding over a panel top at its apex is not snapped up onto it")
    void theApexDoesNotCaptureTheShellOnAPanelItNeverCleared() {
        // The bug this guards: a panel top is solid underfoot but NOT as a ceiling, so a whammed shell
        // rises straight through one. Near the apex its vertical speed is almost nothing while it still
        // travels sideways at 8, so it can slide into a panel's column while sunk ten pixels into it -
        // and without the floor detection's depth limit, the first descending frame would align it to that
        // tile's top line and declare it landed, teleporting it up onto a ledge it never crossed.
        // Prepare - a deep floor so the arc has room, and a panel on the row the apex sinks into
        solids[FLOOR_ROW] = new boolean[COLUMNS];
        solidRow(DEEP_FLOOR_ROW);
        oneWayPlatforms.add(of(APEX_COLUMN, APEX_FEET_ROW));
        final KoopaTroopa troopa = troopaAt(5, DEEP_FLOOR_ROW - 1);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));

        // Execute - 28 frames is the whole rise plus the first descending frame
        tick(troopa, 28);

        // Verify - standing on the panel would mean its top line less the body height
        assertThat(troopa.getPosition().y())
            .as("Still ten pixels below the panel's top line, where its own arc left it")
            .isGreaterThan(APEX_FEET_ROW * (double) TILE_SPRITE_SIZE - TILE_SPRITE_SIZE);
        assertThat(troopa.isGrounded()).as("And not standing on anything").isFalse();

        // Execute - the rest of the fall
        tick(troopa, 80);

        // Verify
        assertThat(troopa.isGrounded()).isTrue();
        assertThat(troopa.getPosition().y())
            .as("It came to rest on the real floor, having passed the panel by")
            .isCloseTo((DEEP_FLOOR_ROW - 1) * (double) TILE_SPRITE_SIZE, within(TOLERANCE));
    }

    @Test
    @DisplayName("A shell descending onto a panel top from above still lands on it")
    void aPanelTopApproachedFromAboveStillCatchesTheShell() {
        // The limit must not cost a legitimate landing: feet entering a surface from above are within
        // 4 px of its top line on that very frame, whatever the fall speed.
        // Prepare - a panel two rows under a troopa with no floor of its own beneath it
        solids[FLOOR_ROW] = new boolean[COLUMNS];
        oneWayPlatforms.add(of(5, WALK_ROW + 2));
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());

        // Execute
        tick(troopa, 30);

        // Verify
        assertThat(troopa.isGrounded()).isTrue();
        assertThat(troopa.getPosition().y())
            .as("Resting with its feet on the panel's top line")
            .isCloseTo((WALK_ROW + 2) * (double) TILE_SPRITE_SIZE - TILE_SPRITE_SIZE, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Block bumped from underneath
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A block bumped under a walking troopa shells it with the weaker throw and no score")
    void aBlockBumpShellsIt() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();
        tick(troopa, 1);
        bumpTheBlockUnder(troopa);

        // Execute
        tick(troopa, 1);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.getWakeUpTicks()).isEqualTo(WAKE_UP_TICKS);
        assertThat(troopa.getVelocity().y())
            .as("The bump throws it -48, weaker than the tail's -80")
            .isEqualTo(BUMP_LAUNCH_Y_VELOCITY);
        assertThat(troopa.isUpsideDown()).as("Flipped vertically").isTrue();
        assertThat(troopa.isFacingRight())
            .as("The flip resets the horizontal facing along with flipping it vertically")
            .isFalse();
        assertThat(troopa.getPendingScoreReward()).as("Going into the shell when defeated means no score").isNull();
        assertThat(troopa.getHitCooldownTicks()).as("This path sets no hit cooldown").isZero();
        verify(tailAttackFlashMotionManager, never()).spawn(any(), any());
    }

    @Test
    @DisplayName("Bumping the block under a resting shell re-launches it and resets its wake-up clock")
    void aBlockBumpAlsoReachesARestingShell() {
        // Prepare - stomped, settled, and some way through its clock
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        tick(troopa, 100);
        assertThat(troopa.isGrounded()).isTrue();
        bumpTheBlockUnder(troopa);

        // Execute
        tick(troopa, 1);

        // Verify
        assertThat(troopa.getVelocity().y()).isEqualTo(BUMP_LAUNCH_Y_VELOCITY);
        assertThat(troopa.getWakeUpTicks()).isEqualTo(WAKE_UP_TICKS);
        assertThat(troopa.getState()).isEqualTo(SHELLED);
    }

    // -------------------------------------------------------------------------
    // Shake, flicker and wake-up
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The shell is still until the wake-up timer falls below 96, then it jitters a pixel")
    void theShakeStartsAtTheShakeThreshold() {
        // Prepare
        final KoopaTroopa troopa = shelledOnTheFloor();

        // Execute & Verify
        boolean everJitteredWhileStill = false;
        while (troopa.getWakeUpTicks() >= SHELL_SHAKE_THRESHOLD_TICKS) {
            troopa.motionUpdate();
            everJitteredWhileStill |= troopa.getShellJitterPixels() != 0;
        }
        assertThat(everJitteredWhileStill).as("Nothing moves above the 96 threshold").isFalse();
        assertThat(troopa.isShellShaking()).isTrue();

        int jitteredFrames = 0;
        while (troopa.getWakeUpTicks() > 0) {
            troopa.motionUpdate();
            if (troopa.getState() == SHELLED) {
                jitteredFrames += troopa.getShellJitterPixels();
            }
        }
        assertThat(jitteredFrames).as("And a great many once it is below it").isPositive();
    }

    @Test
    @DisplayName("The waking feet stay hidden until the timer falls below 80")
    void theFeetAppearAtTheFeetThreshold() {
        // Prepare
        final KoopaTroopa troopa = shelledOnTheFloor();

        // Execute & Verify
        while (troopa.getWakeUpTicks() >= SHELL_FEET_THRESHOLD_TICKS) {
            troopa.motionUpdate();
            assertThat(troopa.isWakingFeetShowing())
                .as("No feet at timer %d", troopa.getWakeUpTicks())
                .isFalse();
        }

        boolean feetEverShowed = false;
        while (troopa.getWakeUpTicks() > 0) {
            troopa.motionUpdate();
            feetEverShowed |= troopa.isWakingFeetShowing();
        }
        assertThat(feetEverShowed).isTrue();
    }

    @Test
    @DisplayName("The feet flicker accelerates toward the wake-up: the same duty, toggled faster")
    void theFeetFlickerAccelerates() {
        // The feet mask table is {2, 2, 4, 8, 16, 16} indexed by the wake-up timer >> 4, and the timer counts
        // DOWN, so the mask shrinks 16 -> 8 -> 4 -> 2 and the feet toggle in runs of 8 ticks, then 4, then 2.
        // What accelerates is the RATE - the proportion of ticks showing feet stays at half throughout -
        // and that ramp is the player's only tell that the shell is about to stand up.
        // Prepare
        final KoopaTroopa troopa = shelledOnTheFloor();
        final int[] onRunsPerBand = new int[SHELL_FEET_THRESHOLD_TICKS / FEET_FLICKER_BAND_TICKS];

        // Execute
        while (troopa.getWakeUpTicks() >= SHELL_FEET_THRESHOLD_TICKS) {
            troopa.motionUpdate();
        }
        boolean previouslyShowing = false;
        while (troopa.getWakeUpTicks() > 0) {
            final int band = troopa.getWakeUpTicks() / FEET_FLICKER_BAND_TICKS;
            final boolean showing = troopa.isWakingFeetShowing();
            if (showing && !previouslyShowing) {
                onRunsPerBand[band]++;
            }
            previouslyShowing = showing;
            troopa.motionUpdate();
        }

        // Verify - bands are indexed by the timer, so walk them downward
        int previousRuns = 0;
        for (int band = onRunsPerBand.length - 1; band >= 0; band--) {
            assertThat(onRunsPerBand[band])
                .as("Band %d (timer %d-%d) toggles at least as often as the band above it",
                    band, band * FEET_FLICKER_BAND_TICKS, band * FEET_FLICKER_BAND_TICKS + 15)
                .isGreaterThanOrEqualTo(previousRuns);
            previousRuns = onRunsPerBand[band];
        }
        assertThat(onRunsPerBand[0])
            .as("The last band before waking toggles several times")
            .isGreaterThan(1);
    }

    @Test
    @DisplayName("The topmost flicker band shows no feet at all, which is the mask table's own quirk")
    void theTopFlickerBandNeverShowsFeet() {
        // The feet are gated on the timer < 80, but the mask for timer >> 4 == 4 is 16 - a bit that is
        // never set anywhere in 64..79. So the feet actually begin at 64, a whole band later than the
        // threshold suggests. Surprising enough to pin rather than leave to be rediscovered.
        // Prepare
        final KoopaTroopa troopa = shelledOnTheFloor();

        // Execute & Verify - asserted at each timer value before the clock advances off it
        while (troopa.getWakeUpTicks() >= 4 * FEET_FLICKER_BAND_TICKS) {
            assertThat(troopa.isWakingFeetShowing())
                .as("No feet at timer %d, below the 80 threshold though it is", troopa.getWakeUpTicks())
                .isFalse();
            troopa.motionUpdate();
        }

        // Execute - the band below it is where they really start
        boolean feetEverShowed = false;
        while (troopa.getWakeUpTicks() > 0) {
            troopa.motionUpdate();
            feetEverShowed |= troopa.isWakingFeetShowing();
        }

        // Verify
        assertThat(feetEverShowed).isTrue();
    }

    @Test
    @DisplayName("The feet never show while the troopa is walking, however far its clock has run")
    void theFeetBelongToTheShellAlone() {
        // Prepare - woken up, so the wake-up timer is at zero and the troopa is back on its feet proper
        final KoopaTroopa troopa = shelledOnTheFloor();
        tick(troopa, FRAMES_TO_EMPTY_THE_WAKE_UP_CLOCK);
        assertThat(troopa.getState()).isEqualTo(NORMAL);

        // Execute & Verify
        for (int frame = 0; frame < 60; frame++) {
            troopa.motionUpdate();
            assertThat(troopa.isWakingFeetShowing()).as("Frame %d", frame).isFalse();
        }
    }

    @Test
    @DisplayName("The wake-up clock is throttled above 96, so 255 units take 415 frames and not 255")
    void theWakeUpClockIsThrottledWhileTheShellIsStill() {
        // The central object loop only takes a unit off the wake-up timer on alternate frames while the timer
        // is at or above 96 and the object is not in its normal state - so 160 units run at
        // half rate and the last 95 at full. The frame mask is 1 here; a held shell gets 3 instead.
        // Prepare
        final KoopaTroopa troopa = shelledOnTheFloor();

        // Execute
        tick(troopa, FRAMES_TO_EMPTY_THE_WAKE_UP_CLOCK - 1);

        // Verify
        assertThat(troopa.getWakeUpTicks()).as("One unit left with a frame to go").isEqualTo(1);
        assertThat(troopa.getState()).isEqualTo(SHELLED);

        // Execute - the loop empties the timer and the wake-up acts on it the same frame
        tick(troopa, 1);

        // Verify
        assertThat(troopa.getWakeUpTicks()).isZero();
        assertThat(troopa.getState()).isEqualTo(NORMAL);
    }

    @Test
    @DisplayName("It stands up right side up, faces the player and walks again, ledge guard included")
    void itWakesUpUprightAndResumesWalking() {
        // Prepare - whammed, so it is both shelled and upside down, on a platform that ends at column 7
        solids[FLOOR_ROW] = new boolean[COLUMNS];
        floorFrom(0, 7);
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));
        assertThat(troopa.isUpsideDown()).isTrue();

        // Execute - let the clock run out and the troopa stand up
        tick(troopa, FRAMES_TO_EMPTY_THE_WAKE_UP_CLOCK + 1);

        // Verify
        assertThat(troopa.getState()).isEqualTo(NORMAL);
        assertThat(troopa.isUpsideDown())
            .as("The wake-up facing replaces the flip state wholesale, clearing the vertical flip")
            .isFalse();

        // Execute - long enough to walk the platform and reach its edge
        tick(troopa, 400);

        // Verify
        assertThat(troopa.getPosition().y())
            .as("The red ledge guard is back on duty, so it never leaves its row")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.isExpired()).isFalse();
    }

    @Test
    @DisplayName("The upside-down flag persists for the whole shell life, not just the launch")
    void theInvertedFlagOutlivesTheLaunch() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));

        // Execute & Verify - still inverted long after it has landed and stopped moving
        for (int frame = 0; frame < 300; frame++) {
            troopa.motionUpdate();
            assertThat(troopa.isUpsideDown()).as("Frame %d", frame).isTrue();
        }
        assertThat(troopa.isGrounded()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Shell rendering, without a scene graph
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("An inverted shell is lifted one quad height, so the mirror leaves it over the same ground")
    void theInvertedShellIsLiftedBackOverItsGround() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(playerAtPixelX(2 * TILE_SPRITE_SIZE));
        final WorldOffset feetAnchor = WorldOffset.of(5f, 3f, Z_DEPTH_ENEMY);

        // Execute
        final WorldOffset drawOffset = troopa.shellDrawOffset(feetAnchor);

        // Verify
        assertThat(drawOffset.y())
            .as("A negative Y scale hangs the quad below its own bottom edge")
            .isEqualTo(feetAnchor.y() + TILE_SIZE_GAME_UNITS);
        assertThat(drawOffset.z()).isEqualTo(feetAnchor.z());
    }

    @Test
    @DisplayName("An upright shell needs no vertical compensation at all")
    void theUprightShellIsDrawnOnItsAnchor() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        final WorldOffset feetAnchor = WorldOffset.of(5f, 3f, Z_DEPTH_ENEMY);

        // Execute
        final WorldOffset drawOffset = troopa.shellDrawOffset(feetAnchor);

        // Verify
        assertThat(troopa.isUpsideDown()).isFalse();
        assertThat(drawOffset.y()).isEqualTo(feetAnchor.y());
        assertThat(drawOffset.x()).as("And no shake yet either").isEqualTo(feetAnchor.x());
    }

    @Test
    @DisplayName("The shake moves the shell a pixel to the left and never anywhere else")
    void theShakeOnlyEverSubtractsAPixel() {
        // Prepare
        final KoopaTroopa troopa = shelledOnTheFloor();
        final WorldOffset feetAnchor = WorldOffset.of(5f, 3f, Z_DEPTH_ENEMY);

        // Execute & Verify
        while (troopa.getWakeUpTicks() > 0) {
            troopa.motionUpdate();
            if (troopa.getState() != SHELLED) {
                break;
            }
            final float drawnX = troopa.shellDrawOffset(feetAnchor).x();
            assertThat(drawnX)
                .as("Timer %d draws either on the anchor or one pixel left of it", troopa.getWakeUpTicks())
                .isIn(feetAnchor.x(), feetAnchor.x() - PIXELS_TO_GAME_UNITS);
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private KoopaTroopa troopaAt(final int column, final int row) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, of(column, row));
        troopa.init();
        return troopa;
    }

    /** A troopa stomped where it stands, so it is a shell resting on the floor with its clock full. */
    private KoopaTroopa shelledOnTheFloor() {
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        return troopa;
    }

    private void tick(final KoopaTroopa troopa, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            troopa.motionUpdate();
        }
    }

    /**
     * Ticks to the next descending floor contact and returns the halved slide it left behind. The
     * halving is the only externally visible marker of a landing, because the rebound clears
     * {@code grounded} again on the very same frame.
     */
    private int tickUntilTheSlideChanges(final KoopaTroopa troopa) {
        final int before = troopa.getVelocity().x();
        for (int frame = 0; frame < 300; frame++) {
            troopa.motionUpdate();
            if (troopa.getVelocity().x() != before) {
                return troopa.getVelocity().x();
            }
        }
        throw new AssertionError("The shell never landed");
    }

    /** Marks the cell under the troopa's feet as carrying a live block bounce. */
    private void bumpTheBlockUnder(final KoopaTroopa troopa) {
        final int centerColumn = troopa.getPosition().plusX(TILE_SPRITE_SIZE / 2.0).column();
        final int feetRow = troopa.getPosition().plusY(TILE_SPRITE_SIZE).row();
        bumpedBlockCells.add(of(centerColumn, feetRow));
    }

    /** A player falling onto the troopa; its position and its kill chain are read by the stomp. */
    private static LevelScenePlayer descendingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setDY(2.0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        return player;
    }

    /** A player at a given sprite-pixel X, also published to the collision grid's closest-player query. */
    private LevelScenePlayer playerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(WALK_ROW_PIXEL_Y);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
        return player;
    }

    private void floorFrom(final int fromColumn, final int toColumn) {
        for (int column = fromColumn; column <= toColumn; column++) {
            solids[FLOOR_ROW][column] = true;
        }
    }

    private void solidColumn(final int column) {
        for (int row = 0; row < ROWS; row++) {
            solids[row][column] = true;
        }
    }

    private void solidRow(final int row) {
        for (int column = 0; column < COLUMNS; column++) {
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

    /**
     * Underfoot, a one-way platform counts too - that is the whole two-tier point of the grid having
     * separate ground and wall/ceiling queries, and it is what lets something rise straight through a
     * panel top and still be able to stand on it.
     */
    private boolean isGroundSolid(final int column, final int row) {
        return isSolid(column, row) || oneWayPlatforms.contains(of(column, row));
    }
}
