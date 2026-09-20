package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
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
import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Two Goombas that walk into each other must turn around and separate, exactly as walking into a solid
 * tile turns one around — the ROM's {@code Object_BumpOffOthers} (dasm prg000 @ PRG000_CC2B), which
 * {@code OBJ_GOOMBA} opts into via {@code OAT_BOUNCEOFFOTHERS}.
 *
 * <p>These drive the real pipeline order: both Goombas move, both are inserted into the broadphase, and
 * only then does the bump pass run — the same sequence {@code GameEngine} uses.
 */
class GoombaBumpTest {
    private static final int COLUMNS = 16;
    private static final int ROWS = 8;
    private static final int WALK_FRAME_COUNT = 2;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

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
    @DisplayName("Two Goombas walking into each other turn around and walk apart")
    void goombasWalkingIntoEachOtherBounceApart() {
        // Prepare - adjacent Goombas made to face each other, so they close at 1px a tick
        final Goomba left = goombaAt(5, WALK_ROW);
        final Goomba right = goombaAt(6, WALK_ROW);
        faceTowardsEachOther(left, right);
        assertThat(left.isFacingRight()).isTrue();
        assertThat(right.isFacingRight()).isFalse();

        // Execute - one tick closes the 0px gap into a 1px overlap, then the bump pass resolves it
        tickAndResolveBumps(left, right);

        // Verify - each has turned away from the other, mirroring the about-face at a wall
        assertThat(left.isFacingRight()).as("The left Goomba now heads left, away").isFalse();
        assertThat(right.isFacingRight()).as("The right Goomba now heads right, away").isTrue();

        // Execute - let them walk on
        final double leftX = left.getPixelX();
        final double rightX = right.getPixelX();
        for (int tick = 0; tick < 16; tick++) {
            tickAndResolveBumps(left, right);
        }

        // Verify - they are moving apart and no longer overlap
        assertThat(left.getPixelX()).as("Walking left").isLessThan(leftX);
        assertThat(right.getPixelX()).as("Walking right").isGreaterThan(rightX);
        assertThat(left.getBounds().intersects(right.getBounds())).as("Separated").isFalse();
    }

    @Test
    @DisplayName("The interpenetration during a bump stays under a pixel")
    void bumpBarelyOverlapsThem() {
        // The bump is detected just after the overlap rather than instead of it (the pass runs once every
        // object has moved), so this pins how much overlap that costs: at half a pixel a tick each, the
        // boxes are never more than a pixel into one another before they start separating.
        final Goomba left = goombaAt(5, WALK_ROW);
        final Goomba right = goombaAt(6, WALK_ROW);
        faceTowardsEachOther(left, right);

        double deepestOverlap = 0;
        for (int tick = 0; tick < 32; tick++) {
            tickAndResolveBumps(left, right);
            final double overlap = left.getBounds().right() - right.getBounds().left();
            deepestOverlap = max(deepestOverlap, overlap);
        }

        assertThat(deepestOverlap).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("A pair that is already heavily overlapped separates instead of flipping in lockstep")
    void heavilyOverlappedPairSeparates() {
        // Prepare - walk them into each other with the bump pass disabled so they end up 10px deep, the
        // state an author stacking placements nearly on top of each other would produce. A blind
        // about-face would flip both every tick and never let them part; turning away from the neighbour
        // is idempotent, so they commit to opposite directions.
        final Goomba left = goombaAt(5, WALK_ROW);
        final Goomba right = goombaAt(6, WALK_ROW);
        faceTowardsEachOther(left, right);
        for (int tick = 0; tick < 10; tick++) {
            left.motionUpdate();
            right.motionUpdate();
        }
        assertThat(left.getBounds().right() - right.getBounds().left())
            .as("Deeply interpenetrating before the first bump")
            .isGreaterThan(8.0);

        // Execute
        for (int tick = 0; tick < 40; tick++) {
            tickAndResolveBumps(left, right);
        }

        // Verify
        assertThat(left.isFacingRight()).isFalse();
        assertThat(right.isFacingRight()).isTrue();
        assertThat(left.getBounds().intersects(right.getBounds())).as("Separated").isFalse();
    }

    @Test
    @DisplayName("A Goomba dropping onto another's head does not turn either of them around")
    void landingOnAHeadIsNotABump() {
        // Prepare - one Goomba directly above another, both walking the same way. Only left/right contact
        // bounces; a vertical overlap must leave the walk untouched.
        final Goomba lower = goombaAt(5, WALK_ROW);
        final Goomba upper = goombaAt(5, WALK_ROW - 1);
        assertThat(lower.isFacingRight()).isFalse();
        assertThat(upper.isFacingRight()).isFalse();

        // Execute - the faller closes on the head and overlaps it vertically
        for (int tick = 0; tick < 8; tick++) {
            tickAndResolveBumps(lower, upper);
        }

        // Verify - they really are overlapping, and neither about-faced
        assertThat(upper.getBounds().intersects(lower.getBounds()))
            .as("The faller has sunk into the head, so a bump would have been dispatched if this counted")
            .isTrue();
        assertThat(lower.isFacingRight()).isFalse();
        assertThat(upper.isFacingRight()).isFalse();
    }

    @Test
    @DisplayName("A Goomba is not turned around by a non-bouncing object it overlaps")
    void nonBouncingNeighbourDoesNotTurnAGoomba() {
        // Prepare - a reward-like object sharing the Goomba's cell. The attribute is checked on both
        // sides, so only fellow bouncers can steer it.
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final ActiveLevelObject reward = mock(ActiveLevelObject.class);
        when(reward.getBounds()).thenReturn(goomba.getBounds());
        when(reward.bouncesOffOtherObjects()).thenReturn(false);

        final ActiveObjectGrid<ActiveLevelObject> broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        broadPhase.insert(goomba);
        broadPhase.insert(reward);

        // Execute
        broadPhase.resolveObjectToObjectBumps();

        // Verify
        assertThat(goomba.isFacingRight()).as("Untouched by the reward").isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Advances both Goombas and then resolves bumps over the fully populated broadphase — the phase
     * order {@code GameEngine} runs: clear, every manager moves and inserts, then object-to-object.
     */
    private void tickAndResolveBumps(final Goomba first, final Goomba second) {
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        first.motionUpdate();
        second.motionUpdate();
        broadPhase.insert(first);
        broadPhase.insert(second);
        broadPhase.resolveObjectToObjectBumps();
    }

    /**
     * Points the two Goombas at one another by dropping a player between them: each one's spawn-time
     * {@code faceClosestPlayer} then resolves to the inward direction.
     */
    private void faceTowardsEachOther(final Goomba left, final Goomba right) {
        final double midpoint = (left.getPixelX() + right.getPixelX()) / 2.0;
        givenPlayerAtPixelX(midpoint);
        left.faceClosestPlayer();
        right.faceClosestPlayer();
    }

    private Goomba goombaAt(final int column, final int row) {
        final Goomba goomba = new Goomba(gameEngine, Offset.of(column, row));
        goomba.init();
        return goomba;
    }

    private void givenPlayerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(WALK_ROW_PIXEL_Y);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
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
