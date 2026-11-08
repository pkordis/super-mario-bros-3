package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.REST_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.START_COLUMN;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.descendingPlayer;
import static house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaShellCapabilities.WAKE_UP_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.placement.EnemyDepthAllocator.baseDepthOfSlot;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every way a Koopa Paratroopa is defeated ends with its slot handed to a walking green
 * {@link KoopaTroopa}: the collide entry is {@code OCSPECIAL_KILLCHANGETO | OBJ_GREENTROOPA} (dasm
 * prg004.asm:114). The paratroopa implements none of the aftermath; the troopa does.
 */
class KoopaParaTroopaDefeatTest {
    private static final double TOLERANCE = 1e-9;
    private static final int STOMP_HIT_COOLDOWN_TICKS = 8;
    private static final int TAIL_STRIKE_HIT_COOLDOWN_TICKS = 12;
    private static final int TAIL_LAUNCH_Y_VELOCITY = -80;
    private static final int BUMP_LAUNCH_Y_VELOCITY = -48;
    private static final int SHELL_KILL_LAUNCH_Y_VELOCITY = -48;
    private static final int KNOCK_X_VELOCITY = 8;
    private static final int SHELL_KNOCK_RIGHT = 16;
    private static final int WALK_SPEED = 8;

    private KoopaParaTroopaTestWorld world;

    @BeforeEach
    void prepare() {
        world = new KoopaParaTroopaTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // Stomp
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A stomp sheds the wing: a walking green troopa at the same sub-pixel spot, at rest vertically")
    void aStompLeavesAWalkingTroopa() {
        // Prepare - caught mid-hop, facing right, so neither the position nor the facing is a default
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);
        world.tick(paratroopa, 5);
        final double x = paratroopa.getPosition().x();
        final double y = paratroopa.getPosition().y();
        assertThat(y % TILE_SPRITE_SIZE).as("Genuinely mid-tile").isNotZero();

        // Execute
        paratroopa.onCollisionFromAbove(descendingPlayer());

        // Verify - PRG000_D2B4 rewrites the ID and PRG000_D31E zeroes YVel; XVel and the state stay
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getMode()).isEqualTo(GREEN);
        assertThat(troopa.getState()).isEqualTo(NORMAL);
        assertThat(troopa.getPosition().x()).isCloseTo(x, within(TOLERANCE));
        assertThat(troopa.getPosition().y()).isCloseTo(y, within(TOLERANCE));
        assertThat(troopa.isFacingRight()).isTrue();
        assertThat(troopa.getVelocity().x()).isEqualTo(WALK_SPEED);
        assertThat(troopa.getVelocity().y()).isZero();
        assertThat(troopa.getHitCooldownTicks()).as("Objects_Timer2 = 8").isEqualTo(STOMP_HIT_COOLDOWN_TICKS);
        assertThat(paratroopa.isExpired()).isTrue();
        assertThat(paratroopa.isHittable()).isFalse();
    }

    @Test
    @DisplayName("The stomp bounces the player and pays the chain score once")
    void theStompPaysOnceAndBounces() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final LevelScenePlayer player = descendingPlayer();

        // Execute
        paratroopa.onCollisionFromAbove(player);
        final KoopaTroopa troopa = world.capturedReplacement();
        paratroopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        assertThat(paratroopa.getPendingScoreReward()).as("Score_Get100PlusPts").isEqualTo(SCORE_100);
        assertThat(troopa.getPendingScoreReward()).isNull();
        assertThat(player.getPosition().getDY()).isCloseTo(PLAYER_STOMP_BOUNCE_YVEL, within(TOLERANCE));
        verify(world.getActiveObjectGrid(), times(1).description("One hand-over only"))
            .spawnEnemy(any(EnemyLevelObject.class));
    }

    @Test
    @DisplayName("One stomp cannot also shell it: the troopa is out of reach while its cooldown runs")
    void oneStompCannotAlsoShellIt() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.onCollisionFromAbove(descendingPlayer());
        final KoopaTroopa troopa = world.capturedReplacement();

        // Execute - the same stomp reaching the new troopa
        troopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        assertThat(troopa.getState()).isEqualTo(NORMAL);
        assertThat(troopa.getPendingScoreReward()).isNull();

        // Execute - once Objects_Timer2 has run out, a second stomp shells it as any troopa
        for (int tick = 0; tick < STOMP_HIT_COOLDOWN_TICKS; tick++) {
            troopa.motionUpdate();
        }
        troopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        assertThat(troopa.getState()).isEqualTo(SHELLED);
    }

    // -------------------------------------------------------------------------
    // Tail
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A tail strike leaves an upside-down green shell with a fresh wake-up clock and no score")
    void aTailStrikeShellsIt() {
        // Prepare - the player on the left, so the shell is thrown to the right
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final LevelScenePlayer player = world.playerAtPixelX((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE);

        // Execute
        paratroopa.onTailAttack(player);

        // Verify - Enemy_Kill's OA3_DIESHELLED branch, which is the troopa's own response
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.isUpsideDown()).isTrue();
        assertThat(troopa.getWakeUpTicks()).isEqualTo(WAKE_UP_TICKS);
        assertThat(troopa.getVelocity().y()).isEqualTo(TAIL_LAUNCH_Y_VELOCITY);
        assertThat(troopa.getVelocity().x()).as("Away from the player").isEqualTo(KNOCK_X_VELOCITY);
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(TAIL_STRIKE_HIT_COOLDOWN_TICKS);
        assertThat(troopa.getPendingScoreReward()).isNull();
        assertThat(paratroopa.getPendingScoreReward()).isNull();
        verify(world.getTailAttackFlashMotionManager()).spawn(any(), any());
        assertThat(paratroopa.isExpired()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Block bump
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Landing on a bumped block: an upside-down shell, thrown -$30, facing wiped, 100 points")
    void aBlockBumpShellsItForAHundredPoints() {
        // Prepare - facing right, with the player on the left; the first tick is a landing
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);
        world.playerAtPixelX((START_COLUMN - 3) * (double) TILE_SPRITE_SIZE);
        world.bumpTheBlockUnder(paratroopa);

        // Execute
        paratroopa.motionUpdate();

        // Verify - Object_HandleBumpUnderneath's ID-change branch
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.isUpsideDown()).as("FlipBits = SPR_VFLIP").isTrue();
        assertThat(troopa.isFacingRight()).as("The assignment wipes SPR_HFLIP").isFalse();
        assertThat(troopa.getVelocity().y()).isEqualTo(BUMP_LAUNCH_Y_VELOCITY);
        assertThat(troopa.getVelocity().x()).as("Away from the player").isEqualTo(KNOCK_X_VELOCITY);
        assertThat(troopa.getPendingScoreReward())
            .as("A flat 100, carried by the troopa because the paratroopa leaves first")
            .isEqualTo(SCORE_100);
        assertThat(paratroopa.isExpired()).isTrue();
        verify(world.getTailAttackFlashMotionManager(), never()).spawn(any(), any());
    }

    @Test
    @DisplayName("A block bumped while it is in the air does nothing until its feet land in it")
    void aBumpOnlyCatchesItOnALanding() {
        // Prepare - launched, then every block it could come down on is bumped
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.motionUpdate();
        world.tick(paratroopa, 10);
        for (int column = START_COLUMN - 4; column <= START_COLUMN + 1; column++) {
            world.getBumpedBlockCells().add(of(column, REST_ROW + 1));
        }

        // Execute
        world.tick(paratroopa, 10);

        // Verify - Object_TileFeet2 is sky the whole way up
        verify(world.getActiveObjectGrid(), never()).spawnEnemy(any());
        assertThat(paratroopa.isExpired()).isFalse();

        // Execute - on to the next landing
        for (int tick = 0; tick < 100 && !paratroopa.isExpired(); tick++) {
            paratroopa.motionUpdate();
        }

        // Verify
        assertThat(world.capturedReplacement().getState()).isEqualTo(SHELLED);
    }

    // -------------------------------------------------------------------------
    // Shells
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A kicked shell kills it through the troopa that takes its slot")
    void aKickedShellKillsIt() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);

        // Execute
        paratroopa.onStruckByKickedObject(SHELL_KNOCK_RIGHT);
        paratroopa.onStruckByKickedObject(SHELL_KNOCK_RIGHT);

        // Verify
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getState()).isEqualTo(KILLED);
        assertThat(troopa.getVelocity().x()).isEqualTo(SHELL_KNOCK_RIGHT);
        assertThat(troopa.getVelocity().y()).isEqualTo(SHELL_KILL_LAUNCH_Y_VELOCITY);
        assertThat(paratroopa.isExpired()).isTrue();
    }

    @Test
    @DisplayName("A carried shell walked into it kills it the same way")
    void aCarriedShellKillsIt() {
        // Prepare - a held shell whose holder stands to the left
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final LevelScenePlayer holder = world.playerAtPixelX((START_COLUMN - 2) * (double) TILE_SPRITE_SIZE);
        final CarriableLevelObject carried = mock(CarriableLevelObject.class);
        when(carried.getCarrier()).thenReturn(holder);
        doCallRealMethod().when(carried).onCarriedInto(any());

        // Execute
        carried.onCarriedInto(paratroopa);

        // Verify
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getState()).isEqualTo(KILLED);
        assertThat(troopa.getVelocity().x()).as("Knocked away from the holder").isPositive();
    }

    // -------------------------------------------------------------------------
    // The hand-over
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The troopa keeps the placement and the depth, and adopts the slot")
    void theTroopaKeepsThePlacementAndTheDepth() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setBaseDepth(baseDepthOfSlot(13));

        // Execute
        paratroopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        final KoopaTroopa troopa = world.capturedReplacement();
        verify(world.getActiveObjectGrid()).handOverPlacement(paratroopa, troopa);
        assertThat(troopa.getBaseDepth()).isCloseTo(baseDepthOfSlot(13), within(1e-7f));
        assertThat(troopa.hasAdoptedASlot()).as("So its manager moves it on the tick it arrives").isTrue();
    }

    @Test
    @DisplayName("Arriving in the scene, the troopa neither re-faces the player nor erases a placement")
    void theTroopaDoesNotReFaceOrErase() {
        // Prepare - the paratroopa walks left, then the player moves to its right
        world.playerAtPixelX(0);
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.faceClosestPlayer();
        assertThat(paratroopa.isFacingRight()).isFalse();
        world.playerAtPixelX((START_COLUMN + 6) * (double) TILE_SPRITE_SIZE);
        paratroopa.onCollisionFromAbove(descendingPlayer());
        final KoopaTroopa troopa = world.capturedReplacement();

        // Execute
        troopa.spawnIntoScene();

        // Verify
        assertThat(troopa.isFacingRight()).as("Kept the inherited direction").isFalse();
        verify(world.getGameEngine(), never()).getLayerGeometry(any());
    }

    // -------------------------------------------------------------------------
    // Hurting the player
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It hurts the player on a body hit and from below, and never once it has handed over")
    void itHurtsThePlayerUntilItHandsOver() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final LevelScenePlayer player = world.playerAtPixelX(START_COLUMN * (double) TILE_SPRITE_SIZE);

        // Execute
        paratroopa.onPlayerOverlap(player);
        paratroopa.onCollisionFromBelow(player);
        paratroopa.onCollisionFromAbove(descendingPlayer());
        paratroopa.onPlayerOverlap(player);
        paratroopa.onCollisionFromBelow(player);

        // Verify
        verify(player, times(2)).getHurtBy(paratroopa);
    }
}
