package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The one behaviour that separates {@code ObjNorm_RedTroopa} from the {@code ObjNorm_GroundTroop} its
 * green twin and the Goomba share: a red troopa turns back at a ledge instead of walking off it.
 *
 * <p>Both colours are the same class, so the mode handed to {@code configure} is the switch - which
 * makes the green cases below as much a test of that plumbing as of the motion.
 */
class KoopaTroopaLedgeTurnTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int WALK_FRAME_COUNT = 2;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

    /** $08/16 px per frame. */
    private static final double WALK_STEP_PIXELS = 0.5;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private boolean[][] solids;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];

        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

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
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Turns back at a ledge instead of walking off it, and never leaves its walking row")
    void turnsBackAtALedge() {
        // Prepare - the floor runs from column 5 rightwards; the troopa starts at column 6 heading left
        floorFrom(5, COLUMNS - 1);
        final KoopaTroopa troopa = troopaAt(6, WALK_ROW);
        troopa.faceClosestPlayer();

        // Execute - 48 frames is 24 px, which leaves its centre still over the last floor tile
        tick(troopa, 48);

        // Verify
        assertThat(troopa.isFacingRight()).as("Still heading left while supported").isFalse();
        assertThat(troopa.isGrounded()).isTrue();

        // Execute - one more frame takes the centre past the edge and trips the guard
        tick(troopa, 1);

        // Verify
        assertThat(troopa.isFacingRight()).as("Object_AboutFace flipped it").isTrue();
        assertThat(troopa.getPosition().y())
            .as("Object_HitGround put it straight back on the row it was patrolling")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.isGrounded()).isTrue();
    }

    @Test
    @DisplayName("The about-face re-applies X velocity twice, undoing the step that cleared the edge")
    void theTurnStepsBackInlandTwice() {
        // Prepare
        floorFrom(5, COLUMNS - 1);
        final KoopaTroopa troopa = troopaAt(6, WALK_ROW);
        troopa.faceClosestPlayer();
        tick(troopa, 48);
        final double beforeTurnX = troopa.getPosition().x();

        // Execute - the frame that trips the guard
        tick(troopa, 1);

        // Verify - one step out, then two back: a net single step in the new direction
        assertThat(troopa.getPosition().x())
            .as("Object_ApplyXVel is called twice (dasm prg004.asm:3845-3846)")
            .isCloseTo(beforeTurnX + WALK_STEP_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("Patrols a narrow platform indefinitely without ever dropping below it")
    void patrolsBetweenBothEdgesForever() {
        // Prepare - a three-tile island with nothing either side of it
        floorFrom(5, 7);
        final KoopaTroopa troopa = troopaAt(6, WALK_ROW);
        troopa.faceClosestPlayer();

        // Execute & Verify - long enough to cross the island several times in both directions
        for (int frame = 0; frame < 600; frame++) {
            troopa.motionUpdate();
            assertThat(troopa.getPosition().y())
                .as("Frame %d: never below the row it is patrolling", frame)
                .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
            assertThat(troopa.isExpired()).as("Frame %d: never falls out of the level", frame).isFalse();
        }

        assertThat(troopa.getPosition().x())
            .as("Still somewhere over the island it started on")
            .isBetween(4 * (double) TILE_SPRITE_SIZE, 8 * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("A troopa that is genuinely airborne about-faces once and then falls (the Var4 latch)")
    void airborneTroopaDoesNotTurnEveryFrame() {
        // Prepare - no floor anywhere under it, so every frame's feet probe comes back empty
        final KoopaTroopa troopa = troopaAt(2, 2);
        troopa.faceClosestPlayer();
        final boolean facingBefore = troopa.isFacingRight();

        // Execute
        tick(troopa, 1);

        // Verify - the first frame with no ground is indistinguishable from stepping off a ledge
        assertThat(troopa.isFacingRight()).as("The guard fires once").isNotEqualTo(facingBefore);
        final boolean facingAfterTurn = troopa.isFacingRight();

        // Execute & Verify - from here the latch holds and it simply falls
        double previousY = troopa.getPosition().y();
        for (int frame = 0; frame < 20; frame++) {
            troopa.motionUpdate();
            assertThat(troopa.isFacingRight())
                .as("Frame %d: Objects_Var4 stops it about-facing again", frame)
                .isEqualTo(facingAfterTurn);
            assertThat(troopa.getPosition().y())
                .as("Frame %d: still falling", frame)
                .isGreaterThan(previousY);
            previousY = troopa.getPosition().y();
        }
    }

    @Test
    @DisplayName("Landing clears the latch, so the next ledge is guarded again")
    void landingRearmsTheGuard() {
        // Prepare - a troopa dropped in mid-air above a platform that ends at column 7
        floorFrom(0, 7);
        final KoopaTroopa troopa = troopaAt(3, 2);
        troopa.faceClosestPlayer();

        // Execute - fall, land, then walk the length of the platform and off its right-hand edge.
        // The initial about-face sends it right, so it approaches the far edge of the floor.
        tick(troopa, 400);

        // Verify
        assertThat(troopa.isGrounded()).as("Back on the floor").isTrue();
        assertThat(troopa.getPosition().y())
            .as("The guard rearmed after landing and caught the second edge too")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.getPosition().x())
            .as("Held on the platform rather than walking off its right-hand end")
            .isLessThan(8 * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Configured green, the same troopa walks straight off the ledge and keeps its facing")
    void aGreenTroopaWalksOffTheLedge() {
        // ObjNorm_GroundTroop has no equivalent of PRG004_B283, so the green troopa reaches the edge
        // and simply stops being supported - exactly as a Goomba does.
        // Prepare - the floor runs from column 5 rightwards; the troopa starts at column 6 heading left
        floorFrom(5, COLUMNS - 1);
        final KoopaTroopa troopa = troopaAt(6, WALK_ROW, GREEN);
        troopa.faceClosestPlayer();

        // Execute - 48 frames walks it to the edge, the rest let it leave the platform behind
        tick(troopa, 70);

        // Verify
        assertThat(troopa.isFacingRight()).as("Nothing ever about-faced it").isFalse();
        assertThat(troopa.getPosition().x())
            .as("Carried on past the platform's left edge")
            .isLessThan(5 * (double) TILE_SPRITE_SIZE);
        assertThat(troopa.getPosition().y())
            .as("No longer on the row it was walking - it went over the side")
            .isGreaterThan(WALK_ROW_PIXEL_Y);
    }

    @Test
    @DisplayName("A green troopa falls without the single about-face the red one's Var4 latch allows")
    void aGreenTroopaNeverAboutFacesInTheAir() {
        // Prepare - no floor anywhere under it, which is the case that makes the red troopa turn once
        final KoopaTroopa troopa = troopaAt(2, 2, GREEN);
        troopa.faceClosestPlayer();
        final boolean facingBefore = troopa.isFacingRight();

        // Execute & Verify - the first frame moves nothing vertically, because Object_Move adds gravity
        // at the END of a frame; from the second on it is falling
        tick(troopa, 1);
        double previousY = troopa.getPosition().y();
        for (int frame = 0; frame < 20; frame++) {
            troopa.motionUpdate();
            assertThat(troopa.isFacingRight())
                .as("Frame %d: the guard is not on this troopa's path at all", frame)
                .isEqualTo(facingBefore);
            assertThat(troopa.getPosition().y())
                .as("Frame %d: still falling", frame)
                .isGreaterThan(previousY);
            previousY = troopa.getPosition().y();
        }
    }

    @Test
    @DisplayName("The placement's mode is what decides it: the same ledge holds the red one and not the green")
    void theModeAloneDecidesWhetherTheLedgeHolds() {
        // Prepare - one platform, two troopas on it, identical but for what configure was handed
        floorFrom(5, COLUMNS - 1);
        final KoopaTroopa red = troopaAt(6, WALK_ROW, RED);
        final KoopaTroopa green = troopaAt(6, WALK_ROW, GREEN);
        red.faceClosestPlayer();
        green.faceClosestPlayer();

        // Execute
        tick(red, 70);
        tick(green, 70);

        // Verify
        assertThat(red.getMode()).isEqualTo(RED);
        assertThat(green.getMode()).isEqualTo(GREEN);
        assertThat(red.getPosition().y())
            .as("The red one is still on the surface it was patrolling")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(green.getPosition().y())
            .as("The green one is below it")
            .isGreaterThan(red.getPosition().y());
    }

    @Test
    @DisplayName("A troopa whose placement names no mode keeps the guard, the safer of the two to assume")
    void anUnconfiguredTroopaIsRed() {
        // Prepare
        floorFrom(5, COLUMNS - 1);
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(6, WALK_ROW));
        troopa.init();
        troopa.configure(new LevelObjectData(Map.of()));
        troopa.faceClosestPlayer();

        // Execute
        tick(troopa, 70);

        // Verify
        assertThat(troopa.getMode()).isEqualTo(RED);
        assertThat(troopa.getPosition().y())
            .as("Held on its platform rather than walking off it")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A red troopa, configured the way {@code EnemySpawner} configures a placed one. */
    private KoopaTroopa troopaAt(final int column, final int row) {
        return troopaAt(column, row, RED);
    }

    private KoopaTroopa troopaAt(
        final int column,
        final int row,
        final KoopaTroopaMode mode
    ) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(column, row));
        troopa.init();
        troopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, mode.name())));
        return troopa;
    }

    private void tick(final KoopaTroopa troopa, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            troopa.motionUpdate();
        }
    }

    private void floorFrom(final int fromColumn, final int toColumn) {
        for (int column = fromColumn; column <= toColumn; column++) {
            solids[FLOOR_ROW][column] = true;
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
