package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
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
 * The de-winging stomp: two stomps to kill a Para-Goomba, because the first one does not kill it at all.
 *
 * <p>{@code $73}'s collide-table entry is {@code OCSPECIAL_KILLCHANGETO | OBJ_GOOMBA}
 * (dasm prg004.asm:119), and the normal stomp path {@code PRG000_D2B4} (prg000.asm:3940) tests the
 * entry's upper byte with no attribute gate at all: it awards the 100 points, bounces the player,
 * overwrites {@code Level_ObjectID,X} with {@code OBJ_GOOMBA} and then <em>gives the hit back</em>, so
 * the slot lives on as a Goomba. The port expires the Para-Goomba and spawns that Goomba in its place.
 */
class ParaGoombaStompTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 24;
    private static final int ROWS = 12;
    private static final int WALK_FRAME_COUNT = 2;

    /** {@code Objects_Timer2 = 8} - how long the fresh Goomba is out of reach of the player. */
    private static final int STOMP_HIT_COOLDOWN_TICKS = 8;

    private static final int FLOOR_ROW = 10;
    private static final int REST_ROW = FLOOR_ROW - 1;
    private static final double REST_ROW_PIXEL_Y = REST_ROW * (double) TILE_SPRITE_SIZE;
    private static final int START_COLUMN = 10;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StaticEnvironmentCollisionGrid collisionGrid;
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

        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
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
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));

        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        // A bare quad is enough: nothing here renders, but spawnIntoScene does attach it to the root.
        when(goombaAnimator.buildSprite(any()))
            .thenAnswer(invocation -> new Geometry("Goomba", new Quad(1f, 1f)));

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(paraGoombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
            .thenReturn(goombaAnimator);
        // The replacement comes out of the bean factory as a prototype, exactly as the score captions
        // do. Building a real one keeps the handover assertions about real state rather than a mock's.
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
    @DisplayName("A stomp sheds the wings and leaves a Goomba standing in the same place")
    void stompLeavesAGoombaBehind() {
        // Prepare - caught mid-hop, so the position under test is deliberately not tile-aligned
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tick(paraGoomba, 5);
        final double stompedAtX = paraGoomba.getPosition().x();
        final double stompedAtY = paraGoomba.getPosition().y();
        assertThat(stompedAtX % TILE_SPRITE_SIZE).as("Genuinely mid-tile horizontally").isNotZero();
        assertThat(stompedAtY % TILE_SPRITE_SIZE).as("And mid-tile vertically").isNotZero();

        // Execute
        paraGoomba.onCollisionFromAbove(descendingPlayer());

        // Verify - the ROM keeps the same slot, so the replacement inherits the live sub-tile position
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.getPosition().x()).isCloseTo(stompedAtX, within(TOLERANCE));
        assertThat(replacement.getPosition().y()).isCloseTo(stompedAtY, within(TOLERANCE));
        assertThat(replacement.getOffset())
            .as("Its placement cell is derived from where it actually is")
            .isEqualTo(Offset.of(stompedAtX / TILE_SPRITE_SIZE, stompedAtY / TILE_SPRITE_SIZE));
    }

    @Test
    @DisplayName("The replacement keeps walking the way the Para-Goomba was travelling")
    void replacementInheritsTheFacing() {
        // Prepare - send it walking right, which is the direction a re-run of ObjInit_GroundTroop would
        // not necessarily reproduce
        givenPlayerAtPixelX((COLUMNS - 2) * (double) TILE_SPRITE_SIZE);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        assertThat(paraGoomba.isFacingRight()).isTrue();

        // Execute
        paraGoomba.onCollisionFromAbove(descendingPlayer());

        // Verify
        final Goomba replacement = captureSpawnedGoomba();
        assertThat(replacement.isFacingRight()).isTrue();
        assertThat(replacement.getVelocity().x()).as("And its velocity follows that facing").isEqualTo(8);
    }

    @Test
    @DisplayName("The replacement starts from rest rather than inheriting the winged fall")
    void replacementStartsFromRest() {
        // Prepare - stomped while falling fast
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, 0);
        paraGoomba.faceClosestPlayer();
        tick(paraGoomba, 10);
        assertThat(paraGoomba.getVelocity().y()).as("Falling when stomped").isPositive();

        // Execute
        paraGoomba.onCollisionFromAbove(descendingPlayer());

        // Verify - PRG000_D31E zeroes YVel before returning
        assertThat(captureSpawnedGoomba().getVelocity().y()).isZero();
    }

    @Test
    @DisplayName("One stomp cannot kill the replacement: it is briefly out of reach")
    void replacementIsBrieflyUntouchable() {
        // Prepare - the ROM's Objects_Timer2 = 8. Without it, the replacement sits inside the player who
        // just stomped it and the same contact would squash it on the next tick.
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        final Goomba replacement = captureSpawnedGoomba();

        // Execute & Verify - its manager keeps it out of the broadphase until the timer runs out
        assertThat(replacement.isHittable()).as("Untouchable the moment it appears").isFalse();
        for (int tick = 0; tick < STOMP_HIT_COOLDOWN_TICKS - 1; tick++) {
            replacement.motionUpdate();
            assertThat(replacement.isHittable())
                .as("Still untouchable after %d of %d ticks", tick + 1, STOMP_HIT_COOLDOWN_TICKS)
                .isFalse();
        }

        // Execute - the 8th tick runs the timer out
        replacement.motionUpdate();

        // Verify
        assertThat(replacement.isHittable()).as("Back in play, and stompable for real").isTrue();
    }

    @Test
    @DisplayName("A stomp during the cooldown neither squashes the replacement nor pays out again")
    void stompDuringTheCooldownIsIgnored() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        final Goomba replacement = captureSpawnedGoomba();

        // Execute - the same stomp reaching the converted object
        final LevelScenePlayer sameStomp = descendingPlayer();
        replacement.onCollisionFromAbove(sameStomp);

        // Verify
        assertThat(replacement.isSquished()).as("Not flattened by the stomp that created it").isFalse();
        assertThat(replacement.getPendingScoreReward()).as("And not paid for twice").isNull();
        assertThat(sameStomp.getPosition().getDY()).as("No second bounce").isCloseTo(2.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Two stomps kill: the second one flattens the replacement as any Goomba")
    void secondStompFlattensTheReplacement() {
        // Prepare - the first stomp, then wait out Objects_Timer2
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        final Goomba replacement = captureSpawnedGoomba();
        tickGoomba(replacement, STOMP_HIT_COOLDOWN_TICKS);

        // Execute
        final LevelScenePlayer secondStomp = descendingPlayer();
        replacement.onCollisionFromAbove(secondStomp);

        // Verify - OA3_SQUASH routes $72 into the flattened 16-frame death
        assertThat(replacement.isSquished()).isTrue();
        assertThat(replacement.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(secondStomp.getPosition().getDY()).isCloseTo(PLAYER_STOMP_BOUNCE_YVEL, within(TOLERANCE));
    }

    @Test
    @DisplayName("The de-winging stomp awards 100 points and bounces the player")
    void stompAwardsPointsAndBouncesThePlayer() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        final LevelScenePlayer player = descendingPlayer();
        assertThat(paraGoomba.getPendingScoreReward()).isNull();

        // Execute
        paraGoomba.onCollisionFromAbove(player);

        // Verify - the hit is given back, but Score_Get100PlusPts has already run
        assertThat(paraGoomba.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(player.getPosition().getDY()).isCloseTo(PLAYER_STOMP_BOUNCE_YVEL, within(TOLERANCE));
    }

    @Test
    @DisplayName("The Para-Goomba expires exactly once, leaving exactly one Goomba")
    void stompExpiresTheParaGoombaExactlyOnce() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        assertThat(paraGoomba.isExpired()).isTrue();
        assertThat(paraGoomba.isHittable()).as("Intangible from the stomp onwards").isFalse();
        paraGoomba.setPendingScoreReward(null);

        // Execute - a second stomp, and a tick, neither of which may produce another Goomba
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        paraGoomba.motionUpdate();

        // Verify
        verify(activeObjectGrid, times(1).description("One replacement only"))
            .spawnEnemy(any(EnemyLevelObject.class));
        assertThat(paraGoomba.getPendingScoreReward()).as("Paid once").isNull();
    }

    @Test
    @DisplayName("The Para-Goomba drives none of the aftermath: it stops ticking the moment it expires")
    void paraGoombaDoesNothingAfterTheHandover() {
        // Prepare - the fall, the grounding and the walk all belong to the replacement
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW - 3);
        paraGoomba.faceClosestPlayer();
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        final double expiredAtX = paraGoomba.getPosition().x();
        final double expiredAtY = paraGoomba.getPosition().y();

        // Execute
        tick(paraGoomba, 30);

        // Verify
        assertThat(paraGoomba.getPosition().x()).isCloseTo(expiredAtX, within(TOLERANCE));
        assertThat(paraGoomba.getPosition().y()).as("Did not fall, so nothing is duplicated").isCloseTo(
            expiredAtY,
            within(TOLERANCE)
        );
    }

    @Test
    @DisplayName("The replacement neither re-faces the player nor erases a placement cell it never had")
    void replacementDoesNotReFaceOrEraseACell() {
        // Prepare - the Para-Goomba already erased that cell when it spawned, and the direction is
        // settled. getLayerGeometry is deliberately left unstubbed: an erase attempt would fail here.
        givenPlayerAtPixelX(0);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        assertThat(paraGoomba.isFacingRight()).as("Walking left, away from nothing").isFalse();
        givenPlayerAtPixelX((COLUMNS - 2) * (double) TILE_SPRITE_SIZE);
        paraGoomba.onCollisionFromAbove(descendingPlayer());
        final Goomba replacement = captureSpawnedGoomba();

        // Execute - the manager's first-sight moment for the newcomer
        replacement.spawnIntoScene();

        // Verify - a re-run of faceClosestPlayer would have turned it towards the player on the right
        assertThat(replacement.isSpawnedIntoScene()).isTrue();
        assertThat(replacement.isFacingRight()).as("Kept the inherited direction").isFalse();
        verify(gameEngine, never()).getLayerGeometry(any());
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

    private void tickGoomba(final Goomba goomba, final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            goomba.motionUpdate();
        }
    }

    private void givenPlayerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(REST_ROW_PIXEL_Y);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
    }

    /** A player falling onto the enemy; only its position is read by the stomp response. */
    private static LevelScenePlayer descendingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setDY(2.0);
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
