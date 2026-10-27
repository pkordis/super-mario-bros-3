package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A tail attack kills a Para-Goomba outright, wings and all - one hit, not two.
 *
 * <p>{@code Object_RespondToTailAttack} (dasm prg000.asm:5906) falls into {@code Enemy_Kill} with no
 * collide-table gate, so {@code $73}'s {@code OCSPECIAL_KILLCHANGETO} never fires and there is no
 * de-winged stage to survive.
 *
 * <p>What the port does with it is nonetheless a <b>slot handover</b>: the Para-Goomba hands its slot to
 * a ground {@link Goomba} - the same handover the stomp performs, minus the {@code Objects_Timer2} grace
 * the kill path never sets - and that Goomba takes the blow. The corpse the ROM throws is a ground
 * Goomba's anyway: {@code KILLACT_JUSTDRAWMIRROR} (prg004.asm:328) stops running {@code ParaGoomba_Draw},
 * so the wings go, and the shared {@code ObjState_Killed} tumbles the bare body off the level. So these
 * tests assert the handover and the strike landing on the replacement; the throw itself is
 * {@code GoombaTailAttackTest}'s subject.
 */
class ParaGoombaTailAttackTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 24;
    private static final int ROWS = 12;
    private static final int WALK_FRAME_COUNT = 2;

    /** {@code Player_X ± $10} / {@code Player_Y + $10}. */
    private static final double TAIL_OFFSET = 0x10;

    /** {@code ObjectToObject_HitXVel}'s rightward entry, as a carried shell resolves it. */
    private static final int SHELL_KNOCK_RIGHT = 0x08;

    private static final int FLOOR_ROW = 10;
    private static final int REST_ROW = FLOOR_ROW - 1;
    private static final double REST_ROW_PIXEL_Y = REST_ROW * (double) TILE_SPRITE_SIZE;
    private static final int START_COLUMN = 10;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private TailAttackFlashMotionManager tailAttackFlashMotionManager;
    private ActiveObjectGrid<ActiveLevelObject> activeObjectGrid;
    private GameEngine gameEngine;
    private boolean[][] solids;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }

        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        activeObjectGrid = mock(ActiveObjectGrid.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getActiveObjectGrid()).thenReturn(activeObjectGrid);

        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        tailAttackFlashMotionManager = mock(TailAttackFlashMotionManager.class);

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(paraGoombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
            .thenReturn(goombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TailAttackFlashMotionManager.class))
            .thenReturn(tailAttackFlashMotionManager);
        // The replacement comes out of the bean factory as a prototype, exactly as the stomp's does.
        // Building a real one keeps the assertions about real state rather than a mock's.
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(Goomba.class), any(), any()))
            .thenAnswer(invocation -> {
                final Goomba goomba = new Goomba(gameEngine, invocation.getArgument(2));
                goomba.init();
                return goomba;
            });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A tail attack hands the slot to a Goomba and kills that Goomba on the spot")
    void tailAttackKillsThroughTheReplacement() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        assertThat(paraGoomba.isHittable()).isTrue();

        // Execute
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Verify - the Para-Goomba is gone and the replacement is already dead, not walking
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.getWhamMotion()).as("Struck in the same breath it was handed the slot").isNotNull();
        assertThat(replacement.isSquished()).as("Thrown, not flattened").isFalse();
        assertThat(replacement.isHittable()).as("And intangible from then on").isFalse();
        assertThat(paraGoomba.isTailStruck()).isTrue();
        assertThat(paraGoomba.isDeWinged()).as("The de-winging stomp path was not taken").isFalse();
        assertThat(paraGoomba.isExpired()).isTrue();
        assertThat(paraGoomba.isHittable()).as("Intangible from the blow onwards").isFalse();
    }

    @Test
    @DisplayName("The replacement arrives with no hit cooldown, so the strike that created it lands")
    void replacementArrivesWithoutTheStompCooldown() {
        // Prepare - the stomp path's Objects_Timer2 = 8 would swallow the blow; the kill path sets none
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);

        // Execute
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Verify
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.getHitCooldownTicks()).isZero();
        assertThat(replacement.getWhamMotion()).as("Which is why the strike registered").isNotNull();
    }

    @Test
    @DisplayName("The replacement is thrown from exactly where the Para-Goomba was struck")
    void replacementInheritsTheStrikePosition() {
        // Prepare - caught mid-hop, so the position under test is deliberately not tile-aligned
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tick(paraGoomba, 5);
        final double struckAtX = paraGoomba.getPosition().x();
        final double struckAtY = paraGoomba.getPosition().y();
        assertThat(struckAtY % TILE_SPRITE_SIZE).as("Genuinely mid-tile vertically").isNotZero();

        // Execute
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Verify - the ROM keeps the same slot, so the sub-tile position carries across
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.getPosition().x()).isCloseTo(struckAtX, within(TOLERANCE));
        assertThat(replacement.getPosition().y()).isCloseTo(struckAtY, within(TOLERANCE));
        assertThat(replacement.getOffset())
            .as("Its placement cell is derived from where it actually is")
            .isEqualTo(Offset.of(struckAtX / TILE_SPRITE_SIZE, struckAtY / TILE_SPRITE_SIZE));
    }

    @Test
    @DisplayName("The 100-point caption is owed by the replacement, and owed exactly once")
    void oneHundredPointsIsQueuedOnTheReplacement() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);

        // Execute
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Verify - both managers run postCollision, so a caption on each would pay twice
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(paraGoomba.getPendingScoreReward()).as("Not also paid by the Para-Goomba").isNull();

        // Execute - the manager takes it in postCollision, and ticking must not re-queue it
        replacement.setPendingScoreReward(null);
        replacement.motionUpdate();

        // Verify
        assertThat(replacement.getPendingScoreReward()).as("Awarded once").isNull();
    }

    @Test
    @DisplayName("The flash lands on the side it was struck from, between the player and the enemy")
    void flashLandsOnTheStruckSide() {
        // Prepare - struck from the left
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        final double playerX = (START_COLUMN - 1) * (double) TILE_SPRITE_SIZE;

        // Execute
        paraGoomba.onTailAttack(playerAt(playerX));

        // Verify - the replacement spawns it, from the position it inherited
        final Offset spawnedAt = captureFlashOffset();
        assertThat(spawnedAt.x()).as("Player_X + $10").isEqualTo((int) (playerX + TAIL_OFFSET));
        assertThat(spawnedAt.y()).as("Player_Y + $10").isEqualTo((int) (REST_ROW_PIXEL_Y + TAIL_OFFSET));
        assertThat(spawnedAt.x())
            .as("Between the player and the Para-Goomba")
            .isBetween((int) playerX, (int) paraGoomba.getPosition().x() + TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("The replacement holds its ground for one tick, then flips over and is thrown clear")
    void replacementFlipsAndLaunchesOnTheSecondTick() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));
        final Goomba replacement = captureSpawnedGoomba();
        final double struckAtX = replacement.getPosition().x();
        final double struckAtY = replacement.getPosition().y();

        // Execute - the tick the flash appears on
        replacement.motionUpdate();

        // Verify - exactly where the Para-Goomba was hit, still the right way up and not walking
        assertThat(replacement.getPosition().x()).isCloseTo(struckAtX, within(TOLERANCE));
        assertThat(replacement.getPosition().y()).isCloseTo(struckAtY, within(TOLERANCE));
        assertThat(replacement.getWhamMotion().isUpsideDown()).isFalse();

        // Execute
        replacement.motionUpdate();

        // Verify - inverted, rising, and knocked away from the player
        assertThat(replacement.getWhamMotion().isUpsideDown()).isTrue();
        assertThat(replacement.getPosition().y()).as("Thrown upward").isLessThan(struckAtY);
        assertThat(replacement.getPosition().x()).as("And away from the player").isGreaterThan(struckAtX);
    }

    @Test
    @DisplayName("The thrown replacement falls through the floor rather than landing on it")
    void thrownReplacementIgnoresTerrain() {
        // Prepare - land the Para-Goomba first, so it genuinely rests on that floor when the blow arrives
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        paraGoomba.motionUpdate();
        assertThat(paraGoomba.isGrounded()).isTrue();
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));
        final Goomba replacement = captureSpawnedGoomba();

        // Execute
        double deepest = replacement.getPosition().y();
        for (int tick = 0; tick < 100; tick++) {
            replacement.motionUpdate();
            deepest = max(deepest, replacement.getPosition().y());
        }

        // Verify - ObjState_Killed runs Object_Move with no ground alignment
        assertThat(deepest)
            .as("Sank below the floor the Para-Goomba had been standing on")
            .isGreaterThan(FLOOR_ROW * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("The Para-Goomba drives none of the aftermath: it stops ticking the moment it expires")
    void paraGoombaDoesNothingAfterTheHandover() {
        // Prepare - the throw and the fall belong to the replacement, so nothing may be duplicated here
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW - 3);
        paraGoomba.faceClosestPlayer();
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));
        final double expiredAtX = paraGoomba.getPosition().x();
        final double expiredAtY = paraGoomba.getPosition().y();
        final int hopCounterAtDeath = paraGoomba.getHopCounter();

        // Execute
        tick(paraGoomba, 30);

        // Verify
        assertThat(paraGoomba.getPosition().x()).isCloseTo(expiredAtX, within(TOLERANCE));
        assertThat(paraGoomba.getPosition().y()).as("Did not fall").isCloseTo(expiredAtY, within(TOLERANCE));
        assertThat(paraGoomba.getHopCounter()).as("The hop cycle stopped dead").isEqualTo(hopCounterAtDeath);
    }

    @Test
    @DisplayName("A second tail attack neither hands over a second Goomba nor re-flashes")
    void secondTailAttackIsIgnored() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Execute - the same blow reaching it again, and a tick on top
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));
        paraGoomba.motionUpdate();

        // Verify
        verify(activeObjectGrid, times(1).description("One replacement only"))
            .spawnEnemy(any(EnemyLevelObject.class));
        verify(tailAttackFlashMotionManager, times(1).description("One flash only"))
            .spawn(eq(gameEngine), any(Offset.class));
    }

    @Test
    @DisplayName("A stomp on an already tail-struck Para-Goomba hands over nothing and pays nothing")
    void stompAfterTailAttackIsIgnored() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Execute
        paraGoomba.onCollisionFromAbove(playerAt(START_COLUMN * (double) TILE_SPRITE_SIZE));

        // Verify
        assertThat(paraGoomba.isDeWinged()).isFalse();
        assertThat(paraGoomba.getPendingScoreReward()).isNull();
        verify(activeObjectGrid, times(1).description("Only the kill's replacement"))
            .spawnEnemy(any(EnemyLevelObject.class));
    }

    // -------------------------------------------------------------------------
    // The same one-hit kill, dealt by a shell instead of a tail
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A shell rolled or carried into a Para-Goomba kills it outright, wings and all")
    void aKickedObjectKillsThroughTheReplacement() {
        // ObjectKill_SetShellKillVars (prg000.asm:2689-2706) is state-only and knows nothing about what
        // it is killing, so there is no de-winging branch for it to take either.
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();

        // Execute
        paraGoomba.onStruckByKickedObject(SHELL_KNOCK_RIGHT);

        // Verify
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.getWhamMotion()).as("Struck in the same breath it was handed the slot").isNotNull();
        assertThat(replacement.isSquished()).as("Thrown, not flattened").isFalse();
        assertThat(paraGoomba.isDeWinged()).as("The de-winging stomp path was not taken").isFalse();
        assertThat(paraGoomba.isExpired()).isTrue();
        assertThat(paraGoomba.isHittable()).as("Intangible from the blow onwards").isFalse();
    }

    @Test
    @DisplayName("A Para-Goomba already finished cannot be killed again by a shell")
    void aFinishedParaGoombaIsNotKilledTwice() {
        // Prepare - stomped first, so it has already handed its slot over once
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onTailAttack(playerAt((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE));

        // Execute
        paraGoomba.onStruckByKickedObject(SHELL_KNOCK_RIGHT);

        // Verify - one handover, not two
        verify(activeObjectGrid, times(1)).spawnEnemy(any());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** @return the single Goomba handed to the single-instance spawn path */
    private Goomba captureSpawnedGoomba() {
        final ArgumentCaptor<EnemyLevelObject> captor = ArgumentCaptor.forClass(EnemyLevelObject.class);
        verify(activeObjectGrid).spawnEnemy(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Goomba.class);
        return (Goomba) captor.getValue();
    }

    /** @return the single point the flash was spawned at */
    private Offset captureFlashOffset() {
        final ArgumentCaptor<Offset> offsetCaptor = ArgumentCaptor.forClass(Offset.class);
        verify(tailAttackFlashMotionManager).spawn(eq(gameEngine), offsetCaptor.capture());
        return offsetCaptor.getValue();
    }

    private ParaGoomba paraGoombaAt(final int column, final int row) {
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, Offset.of(column, row));
        paraGoomba.init();
        return paraGoomba;
    }

    private void tick(final ParaGoomba paraGoomba, final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            paraGoomba.motionUpdate();
        }
    }

    /** A raccoon player on the Para-Goomba's row; its position and its kill chain are read. */
    private static LevelScenePlayer playerAt(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(REST_ROW_PIXEL_Y);
        position.setDY(1.0);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
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
