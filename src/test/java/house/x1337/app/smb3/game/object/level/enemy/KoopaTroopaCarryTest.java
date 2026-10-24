package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_BEHIND_PLAYER;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_IN_FRONT_OF_PLAYER;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.HELD;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.CARRY_DROP_PIXELS;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.CARRY_DROP_SMALL_PIXELS;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.CARRY_SIDE_OFFSET_PIXELS;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.CARRY_TUCK_SIDE_OFFSET_PIXELS;
import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.CARRY_TUCK_SIDE_SWAP_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.GroundWalkingEnemy.STOMP_HIT_COOLDOWN_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaTroopaFamilyEnemy.CRUSH_LAUNCH_Y_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaTroopaFamilyEnemy.WAKE_UP_TICKS;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.TURN_AROUND_FACE_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Koopa Troopa in the player's hands: what can be picked up, what being carried does to it, and
 * what happens when it is let go.
 *
 * <p>The contact decision is {@code Object_HoldKickOrHurtPlayer} (dasm prg000 @ PRG000_D336) and the
 * carry itself is {@code ObjState_Held} (@ PRG000_CE1F). Two things are deliberately <b>not</b> the
 * ROM's and are called out where they are pinned: releasing B drops the shell where it stands instead
 * of kicking it (SMB3-10 owns the kick), and a shell on its post-stomp hit cooldown cannot be grabbed,
 * which exists only to keep that drop-in-place from reading as a fresh pickup.
 */
class KoopaTroopaCarryTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 24;
    private static final int ROWS = 10;
    private static final int WALK_FRAME_COUNT = 2;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;

    /** The player's sprite is two tiles tall and its feet are always at {@code Y + 32}, either size. */
    private static final double PLAYER_STANDING_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE - 2 * TILE_SPRITE_SIZE;
    private static final double PLAYER_X = 5 * TILE_SPRITE_SIZE;

    /** Comfortably past the 415 frames {@code Objects_Timer3} needs, so a stuck carry fails loudly. */
    private static final int WAKE_UP_CLOCK_FRAME_BUDGET = 600;

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
    // What can be picked up
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A walking troopa cannot be picked up under any input, and still hurts the player")
    void aWalkingTroopaIsNeverCarriable() {
        // Carriability is a question about state, not about type: Object_HoldKickOrHurtPlayer tests
        // Objects_State == OBJSTATE_SHELLED before it looks at the pad at all.
        // Prepare - B held, which is the only input that could ever pick anything up
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final LevelScenePlayer player = player(RIGHT, true, false);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        assertThat(troopa.isCarriable()).as("walking is not carriable").isFalse();
        assertThat(troopa.getState()).isEqualTo(NORMAL);
        assertThat(player.getRuntimeState().getCarriedObject()).isNull();
        verify(player).onHurt();
    }

    @Test
    @DisplayName("Touching a shelled troopa with B held picks it up; the carry holds the instance itself")
    void bHeldPicksUpAShell() {
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(HELD);
        assertThat(troopa.isCarried()).isTrue();
        assertThat(player.getRuntimeState().getCarriedObject())
            .as("a reference, not a flag - the carry repositions this very object every tick")
            .isSameAs(troopa);
        verify(player, never()).onHurt();
    }

    @Test
    @DisplayName("Without B the contact does nothing at all: no pickup, and no hurt either")
    void withoutBNothingHappensYet() {
        // This is SMB3-10's slot. The ROM jumps to Player_KickObject here; until then a shell touched
        // without B is simply inert, which is the one thing that must not be mistaken for the kick.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, false, false);

        // Execute
        troopa.onPlayerOverlap(player);
        troopa.onCollisionFromBelow(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(player.getRuntimeState().getCarriedObject()).isNull();
        verify(player, never()).onHurt();
    }

    @Test
    @DisplayName("A shell is reachable in the broadphase while unable to hurt the player or be stomped")
    void aShellIsReachableWithoutBeingDangerous() {
        // The two concerns isHittable() used to conflate. A shell has to stay in the broadphase or the
        // pickup, the kick and the tail could never find it - and it must still be safe to walk into.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();

        // Execute & Verify
        assertThat(troopa.isHittable()).as("Obj2Obj_EnByState enables the shelled state").isTrue();
        assertThat(troopa.canHurtPlayer()).as("but the hurt branch is never reached").isFalse();

        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onCarried(player);
        assertThat(troopa.isHittable()).as("and so is the held state").isTrue();
        assertThat(troopa.canHurtPlayer()).isFalse();
    }

    @Test
    @DisplayName("A freshly stomped shell is left alone until its hit cooldown expires")
    void theHitCooldownHoldsOffThePickup() {
        // Deliberately NOT the ROM, which branches on the state before the Objects_Timer2 gate. It is
        // the drop-in-place scaffold that needs it: see KoopaTroopa#isCarriable.
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        final LevelScenePlayer player = player(RIGHT, true, false);

        // Execute & Verify
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(STOMP_HIT_COOLDOWN_TICKS);
        assertThat(troopa.isCarriable()).isFalse();
        troopa.onPlayerOverlap(player);
        assertThat(troopa.getState()).isEqualTo(SHELLED);

        // Execute - the cooldown runs out
        tick(troopa, STOMP_HIT_COOLDOWN_TICKS);

        // Verify
        assertThat(troopa.isCarriable()).isTrue();
    }

    @Test
    @DisplayName("A player whose hands were full on this tick's first frame takes nothing else")
    void noSecondObjectWhileCarrying() {
        // Player_ISHolding_OLD, and the reason the lag exists: the drop runs in the object loop and this
        // test runs in the player collision pass that follows it, so the frame that let one shell go
        // must not be able to take the next.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, true);

        // Execute
        troopa.onPlayerOverlap(player);

        // Verify
        assertThat(troopa.getState()).as("neither held").isEqualTo(SHELLED);
        verify(player, never()).onHurt();
    }

    // -------------------------------------------------------------------------
    // Being carried
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(PlayerOrientationHorizontal.class)
    @DisplayName("The shell sits 11 px to the side the player faces and 13 px below their tracked Y")
    void theCarryIsPositionedByAssignment(final PlayerOrientationHorizontal facing) {
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(facing, true, false);

        // Execute
        troopa.onPlayerOverlap(player);
        troopa.motionUpdate();

        // Verify
        final double expectedSide = facing == RIGHT ? CARRY_SIDE_OFFSET_PIXELS : -CARRY_SIDE_OFFSET_PIXELS;
        assertThat(troopa.getPosition().x())
            .as("ObjectHoldXOff is +$0B on the facing side")
            .isCloseTo(PLAYER_X + expectedSide, within(TOLERANCE));
        assertThat(troopa.getPosition().y())
            .isCloseTo(PLAYER_STANDING_Y + CARRY_DROP_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("A small player holds it two pixels lower")
    void theSmallCarryDropsTwoPixelsFurther() {
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        when(player.isSmall()).thenReturn(true);

        // Execute
        troopa.onPlayerOverlap(player);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.getPosition().y())
            .as("$0F when Player_Suit is small, against $0D otherwise")
            .isCloseTo(PLAYER_STANDING_Y + CARRY_DROP_SMALL_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("It follows the player and runs no physics of its own: no gravity, no velocity")
    void theCarryIsDrivenRatherThanSimulated() {
        // ObjState_Held never calls Object_Move, so nothing is integrated while it is held.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);

        // Execute - the player walks four pixels right, a tick at a time
        for (int step = 1; step <= 4; step++) {
            player.getPosition().setX(PLAYER_X + step);
            troopa.motionUpdate();

            // Verify
            assertThat(troopa.getPosition().x())
                .as("step %d tracks the player exactly", step)
                .isCloseTo(PLAYER_X + step + CARRY_SIDE_OFFSET_PIXELS, within(TOLERANCE));
            assertThat(troopa.getPosition().y())
                .as("step %d has not fallen a pixel", step)
                .isCloseTo(PLAYER_STANDING_Y + CARRY_DROP_PIXELS, within(TOLERANCE));
            assertThat(troopa.getVelocity().y()).as("step %d accrued no gravity", step).isZero();
        }
    }

    @Test
    @DisplayName("You carry an upside-down shell upside down, and it is drawn in front of the player")
    void anInvertedShellStaysInvertedInTheHands() {
        // SPR_VFLIP is persistent object state and ObjState_Held never touches FlipBits, so a shell
        // tail-whammed on the way in is held the way it arrived.
        // Prepare - a tail strike inverts it; the cooldown then has to clear before it can be grabbed
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onTailAttack(descendingPlayer());
        tick(troopa, 20);
        final LevelScenePlayer player = player(RIGHT, true, false);

        // Execute
        troopa.onPlayerOverlap(player);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.getState()).isEqualTo(HELD);
        assertThat(troopa.isUpsideDown()).as("no righting on pickup").isTrue();

        final WorldOffset feetAnchor = WorldOffset.of(5f, 3f, Z_DEPTH_ENEMY);
        final WorldOffset drawOffset = troopa.shellDrawOffset(feetAnchor);
        assertThat(drawOffset.y())
            .as("the inversion's one-quad lift composes with the carry rather than being replaced by it")
            .isGreaterThan(feetAnchor.y());
        assertThat(drawOffset.z())
            .as("drawn just behind its holder, so the hands stay visible around it")
            .isEqualTo(FOREGROUND.getPlayerZ() - Z_OFFSET_CARRIED_BEHIND_PLAYER);
        assertThat(drawOffset.z())
            .as("and still ahead of the enemies it is carried past")
            .isGreaterThan(Z_DEPTH_ENEMY);
    }

    @Test
    @DisplayName("The player is drawn in front of what they carry, except during the turn-around")
    void theCarriedShellPassesBehindThePlayerExceptWhenFacingTheCamera() {
        // An object's OAM slot comes from SprRamOffsets and is always $40 or higher (dasm
        // prg000.asm:590) against the player's root of $28 (prg008.asm:480), and a lower index draws in
        // front - so the player wins. The exception is ObjState_Held forcing slot $10 while
        // Player_PipeFace runs (@ PRG000_CF1A), which is below $28 and therefore in front.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);
        final WorldOffset feetAnchor = WorldOffset.of(5f, 3f, Z_DEPTH_ENEMY);

        // Execute & Verify - ordinarily behind
        assertThat(troopa.shellDrawOffset(feetAnchor).z())
            .as("behind the player")
            .isLessThan(FOREGROUND.getPlayerZ());

        // Execute & Verify - and in front for the few ticks of the camera-facing pose
        player.getRuntimeState().setTurnAroundFaceCounter(TURN_AROUND_FACE_TICKS);
        assertThat(troopa.shellDrawOffset(feetAnchor).z())
            .as("held out towards the viewer while facing them")
            .isEqualTo(FOREGROUND.getPlayerZ() + Z_OFFSET_CARRIED_IN_FRONT_OF_PLAYER);
    }

    @Test
    @DisplayName("Dropped, it goes back to the enemy band rather than tracking the player's layer")
    void aDroppedShellLeavesTheCarriedDepthBand() {
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);
        final WorldOffset feetAnchor = WorldOffset.of(5f, 3f, Z_DEPTH_ENEMY);

        // Execute
        when(player.isCarryButtonHeld()).thenReturn(false);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.shellDrawOffset(feetAnchor).z()).isEqualTo(Z_DEPTH_ENEMY);
    }

    @Test
    @DisplayName("Turning around tucks the shell in and crosses it towards the new facing side")
    void theTurnAroundTucksTheShellIn() {
        // ObjectHoldXOff entries 2..4, which the Player_PipeFace branch selects by adding 2 to the index
        // and one more while the counter is still at or above 5 (dasm prg000 @ PRG000_CF0E). For a
        // right-facing player that is index 3 (-$04) first and index 2 (+$04) after - so the shell
        // starts on the side he came FROM and crosses to the side he now faces.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);

        // Execute - early in the pose, which the object loop sees as 6
        player.getRuntimeState().setTurnAroundFaceCounter(TURN_AROUND_FACE_TICKS - 1);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.getPosition().x())
            .as("still trailing on his old left-hand side")
            .isCloseTo(PLAYER_X - CARRY_TUCK_SIDE_OFFSET_PIXELS, within(TOLERANCE));

        // Execute - past the swap, which the object loop sees as 4
        player.getRuntimeState().setTurnAroundFaceCounter(CARRY_TUCK_SIDE_SWAP_TICKS - 1);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.getPosition().x())
            .as("crossed over to the side he is now facing, still tucked in at 4 px")
            .isCloseTo(PLAYER_X + CARRY_TUCK_SIDE_OFFSET_PIXELS, within(TOLERANCE));

        // Execute - the pose over, so the full offset returns
        player.getRuntimeState().setTurnAroundFaceCounter(0);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.getPosition().x())
            .as("held out at arm's length again")
            .isCloseTo(PLAYER_X + CARRY_SIDE_OFFSET_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("A left-facing turn-around mirrors that, crossing the shell the other way")
    void theTurnAroundTuckMirrorsForALeftFacingPlayer() {
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(LEFT, true, false);
        troopa.onPlayerOverlap(player);

        // Execute & Verify - index 4 ($04) first, then index 3 (-$04)
        player.getRuntimeState().setTurnAroundFaceCounter(TURN_AROUND_FACE_TICKS - 1);
        troopa.motionUpdate();
        assertThat(troopa.getPosition().x())
            .isCloseTo(PLAYER_X + CARRY_TUCK_SIDE_OFFSET_PIXELS, within(TOLERANCE));

        player.getRuntimeState().setTurnAroundFaceCounter(CARRY_TUCK_SIDE_SWAP_TICKS - 1);
        troopa.motionUpdate();
        assertThat(troopa.getPosition().x())
            .isCloseTo(PLAYER_X - CARRY_TUCK_SIDE_OFFSET_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("A tail strike on a shell being carried hands it straight back")
    void aTailStrikeTakesItOutOfTheHands() {
        // Every exit from the held state has to clear the player's reference, because the player holds
        // one rather than re-asserting Player_IsHolding each tick as the ROM does. Left behind, it would
        // staple a walking troopa to the player.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);

        // Execute
        troopa.onTailAttack(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(player.getRuntimeState().getCarriedObject()).isNull();
        assertThat(player.getRuntimeState().getTurnAroundFaceCounter()).isZero();
    }

    // -------------------------------------------------------------------------
    // The wake-up clock keeps running while held
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Held long enough, the shell breaks free in your hands and comes up walking")
    void theShellBreaksFreeOfTheHands() {
        // The detail most easily dropped, and a real mechanic: Object_ShellDoWakeUp is called from
        // ObjState_Held exactly as it is from ObjState_Shelled.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);
        final int clockOnPickup = troopa.getWakeUpTicks();

        // Execute
        final int framesHeld = tickWhileHeld(troopa);

        // Verify - the player is now standing inside a live troopa
        assertThat(clockOnPickup).as("picked up with most of its clock left").isGreaterThan(200);
        assertThat(framesHeld)
            .as("the clock really did run for the whole carry, at the shelled state's own rate")
            .isGreaterThan(clockOnPickup);
        assertThat(troopa.getState()).isEqualTo(NORMAL);
        assertThat(troopa.isUpsideDown()).as("the whole-byte FlipBits write rights it").isFalse();
        assertThat(player.getRuntimeState().getCarriedObject()).isNull();
        assertThat(troopa.getPendingScoreReward()).as("waking up pays nothing").isNull();
    }

    @Test
    @DisplayName("Pressed into terrain as it wakes, it dies for 100 points and pops upward instead")
    void aShellCrushedAsItWakesDies() {
        // Prepare - a wall in the column the carried shell's right edge reaches
        solidColumn(6);
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);

        // Execute
        tickWhileHeld(troopa);

        // Verify
        assertThat(troopa.getState()).isEqualTo(KILLED);
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(troopa.getVelocity().y())
            .as("YVel = -$40")
            .isEqualTo(CRUSH_LAUNCH_Y_VELOCITY_FIXED_POINT);
        assertThat(troopa.getVelocity().x()).as("horizontal movement halted").isZero();
        assertThat(player.getRuntimeState().getCarriedObject()).isNull();
        assertThat(troopa.isHittable()).as("Obj2Obj_EnByState disables the killed state").isFalse();
    }

    @Test
    @DisplayName("A killed shell tumbles off the bottom of the level and retires itself")
    void theKilledShellFallsAway() {
        // Prepare
        solidColumn(6);
        final KoopaTroopa troopa = grabbableShell();
        troopa.onPlayerOverlap(player(RIGHT, true, false));
        tickWhileHeld(troopa);

        // Execute
        tick(troopa, 200);

        // Verify
        assertThat(troopa.isExpired()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Release
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Releasing B drops the shell where it stands, with the stomp's brief hit cooldown")
    void releasingBDropsItInPlace() {
        // DEVIATION FROM THE ROM, and a deliberate one: BVC Player_KickObject kicks it (dasm prg000 @
        // PRG000_CE2C). SMB3-10 replaces onReleased' body with the kick; this pins the scaffold.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);
        troopa.motionUpdate();
        final double carriedX = troopa.getPosition().x();
        final double carriedY = troopa.getPosition().y();
        final int clockOnRelease = troopa.getWakeUpTicks();

        // Execute - B comes up
        when(player.isCarryButtonHeld()).thenReturn(false);
        troopa.motionUpdate();

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(player.getRuntimeState().getCarriedObject()).isNull();
        assertThat(troopa.getPosition().x()).as("left exactly where it was").isCloseTo(carriedX, within(TOLERANCE));
        assertThat(troopa.getPosition().y()).isCloseTo(carriedY, within(TOLERANCE));
        assertThat(troopa.getVelocity().x()).as("no kick: it is simply let go").isZero();
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(STOMP_HIT_COOLDOWN_TICKS);
        assertThat(troopa.getWakeUpTicks())
            .as("and it resumes with whatever is left on its clock, not a fresh one")
            .isLessThanOrEqualTo(clockOnRelease);
        assertThat(troopa.getWakeUpTicks()).isLessThan(WAKE_UP_TICKS);
    }

    @Test
    @DisplayName("A dropped shell resumes its own physics and falls back to the ground")
    void aDroppedShellResumesItsOwnBehaviour() {
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);
        troopa.motionUpdate();
        when(player.isCarryButtonHeld()).thenReturn(false);
        troopa.motionUpdate();

        // Execute
        tick(troopa, 40);

        // Verify
        assertThat(troopa.isGrounded()).isTrue();
        assertThat(troopa.getPosition().y())
            .as("settled on the floor it was dropped above")
            .isCloseTo(WALK_ROW * (double) TILE_SPRITE_SIZE, within(TOLERANCE));
    }

    @Test
    @DisplayName("The carried anchor is taken from the position it is handed, not the player's live one")
    void theCarriedAnchorFollowsWhicheverPositionItIsGiven() {
        // This is what lets the simulation and the renderer share one answer. The object loop assigns the
        // logical position from the player's live position once a tick; the player's per-frame visual step
        // places the sprite from their *interpolated* position, which at any render rate above 60 Hz is
        // somewhere between two ticks. Deriving the anchor from the player inside both would pin the
        // sprite to the simulation grid while its holder slid smoothly, which reads as a shake.
        // Prepare
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);

        // Execute - half a pixel along, which no simulation tick would ever report
        final PixelPosition interpolated = troopa.carryAnchorFrom(
            player,
            PLAYER_X + 0.5,
            PLAYER_STANDING_Y
        );

        // Verify
        assertThat(interpolated.x())
            .as("offset from the handed position")
            .isCloseTo(PLAYER_X + 0.5 + CARRY_SIDE_OFFSET_PIXELS, within(TOLERANCE));
        assertThat(troopa.getPosition().x())
            .as("while the logical position, which collision reads, is untouched by it")
            .isCloseTo(PLAYER_X + CARRY_SIDE_OFFSET_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("A shell released while pressed into a wall is pulled clear rather than left inside it")
    void aShellReleasedIntoAWallIsPulledClear() {
        // Not the ROM's problem, because the ROM's release is a kick and a kicked shell about-faces its
        // way back out of whatever it starts inside. A stationary drop has nothing to push it out -
        // moveShellHorizontally returns immediately while XVel is zero - so it would settle inside the
        // wall and later stand up inside it.
        // Prepare - a wall in the column the carried shell reaches
        solidColumn(6);
        final KoopaTroopa troopa = grabbableShell();
        final LevelScenePlayer player = player(RIGHT, true, false);
        troopa.onPlayerOverlap(player);
        troopa.motionUpdate();
        assertThat(troopa.getPosition().x())
            .as("held inside the wall, which while held is harmless and faithful")
            .isCloseTo(PLAYER_X + CARRY_SIDE_OFFSET_PIXELS, within(TOLERANCE));

        // Execute
        when(player.isCarryButtonHeld()).thenReturn(false);
        troopa.motionUpdate();

        // Execute - and let it settle
        tick(troopa, 40);

        // Verify
        assertThat(troopa.getPosition().x())
            .as("pulled back to the player's own column, which is legal ground by construction")
            .isCloseTo(PLAYER_X, within(TOLERANCE));
        assertThat(troopa.isGrounded()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private KoopaTroopa troopaAt(final int column, final int row) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(column, row));
        troopa.init();
        return troopa;
    }

    /** A shell whose post-stomp hit cooldown has already run out, so it is ready to be grabbed. */
    private KoopaTroopa grabbableShell() {
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.onCollisionFromAbove(descendingPlayer());
        tick(troopa, STOMP_HIT_COOLDOWN_TICKS);
        troopa.setPendingScoreReward(null);
        return troopa;
    }

    private void tick(final KoopaTroopa troopa, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            troopa.motionUpdate();
        }
    }

    /**
     * Ticks until the carry ends of its own accord, stopping on the very frame it does so nothing past
     * the transition can disturb what is being asserted about it.
     *
     * @return how many frames the object spent in the player's hands
     */
    private int tickWhileHeld(final KoopaTroopa troopa) {
        for (int frame = 1; frame <= WAKE_UP_CLOCK_FRAME_BUDGET; frame++) {
            troopa.motionUpdate();
            if (troopa.getState() != HELD) {
                return frame;
            }
        }
        throw new AssertionError("The shell was still held after %d frames".formatted(WAKE_UP_CLOCK_FRAME_BUDGET));
    }

    /**
     * A player standing on the fixture's floor, with the carry entry points running for real against a
     * real {@link PlayerRuntimeState} so the hand-off between the two sides is the thing under test.
     *
     * @param facing       the direction the carry offset is taken from
     * @param carryButton  whether B is down
     * @param wasCarrying  the previous tick's {@code Player_ISHolding_OLD}
     */
    private static LevelScenePlayer player(
        final PlayerOrientationHorizontal facing,
        final boolean carryButton,
        final boolean wasCarrying
    ) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(PLAYER_X);
        position.setY(PLAYER_STANDING_Y);

        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setWasCarrying(wasCarrying);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(facing, SUSTAINED));
        when(player.getVisibility()).thenReturn(FOREGROUND);
        when(player.isSmall()).thenReturn(false);
        when(player.isCarryButtonHeld()).thenReturn(carryButton);
        when(player.asLevelScenePlayer()).thenReturn(player);
        doCallRealMethod().when(player).isCarrying();
        doCallRealMethod().when(player).canPickUpCarriable();
        doCallRealMethod().when(player).pickUp(any());
        doCallRealMethod().when(player).releaseCarriedObject();
        return player;
    }

    /** A player falling onto the troopa; only its position is read by the stomp and tail responses. */
    private static LevelScenePlayer descendingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setDY(2.0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        return player;
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
