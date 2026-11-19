package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.game.collision.SlopeTerrain;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The walk cycle of ground-walking enemies on long ramps rising to the right, six rows high: faster walking uphill,
 * slower walking downhill.
 */
@TestInstance(PER_CLASS)
class EnemySlopeWalkAnimationTest {
    private static final int BODY_SIZE = 16;
    private static final int FIRST_RAMP_COLUMN = 4;
    private static final int RAMP_ROWS = 6;
    private static final int TICKS = 120;

    private final SlopeTerrain steepRamp = risingRamp("/");
    private final SlopeTerrain gentleRamp = risingRamp("ab");

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(2);
        final KoopaTroopaAnimator koopaTroopaAnimator = mock(KoopaTroopaAnimator.class);
        when(koopaTroopaAnimator.walkFrameCount(any())).thenReturn(2);
        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(2);
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == GoombaAnimator.class) {
                return goombaAnimator;
            }
            if (type == KoopaTroopaAnimator.class) {
                return koopaTroopaAnimator;
            }
            if (type == ParaGoombaAnimator.class) {
                return paraGoombaAnimator;
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    Stream<Arguments> goombaWalks() {
        return Stream.of(
            arguments("up a steep slope", true, true, List.of(4, 5)),
            arguments("up a gentle slope", false, true, List.of(5, 6)),
            arguments("down a gentle slope", false, false, List.of(10, 11)),
            arguments("down a steep slope", true, false, List.of(16))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goombaWalks")
    @DisplayName("A Goomba changes walk frame about every 4 ticks up a steep slope, 5.3 up a gentle one, "
        + "10.7 down a gentle one and 16 down a steep one")
    void aGoombaWalksFasterUphill(
        final String name,
        final boolean steep,
        final boolean uphill,
        final List<Integer> expectedIntervals
    ) {
        // Prepare
        final SlopeTerrain terrain = steep ? steepRamp : gentleRamp;
        final Goomba goomba = goombaOnTheRamp(terrain, uphill);

        // Execute
        final List<Integer> intervals = walkFrameIntervals(goomba, terrain, goomba::getWalkFrameIndex);

        // Verify
        assertThat(intervals).as("Ticks between walk frames").isNotEmpty().allMatch(expectedIntervals::contains);
    }

    @Test
    @DisplayName("A Goomba on flat ground changes walk frame every 8 ticks")
    void aGoombaOnTheFlat() {
        // Prepare - on the floor, left of the ramp, walking away from it
        final Goomba goomba = new Goomba(engineFor(steepRamp), of(0, 0));
        goomba.init();
        goomba.setPosition(goomba.getPosition().withX(3 * TILE_SPRITE_SIZE).withY(10 * TILE_SPRITE_SIZE));
        goomba.setFacingRight(false);

        // Execute
        final List<Integer> changes = new ArrayList<>();
        int walkFrame = goomba.getWalkFrameIndex();
        for (int tick = 1; tick <= 40; tick++) {
            goomba.motionUpdate();
            if (goomba.getWalkFrameIndex() != walkFrame) {
                walkFrame = goomba.getWalkFrameIndex();
                changes.add(tick);
            }
        }

        // Verify
        assertThat(changes).containsExactly(8, 16, 24, 32, 40);
    }

    @Test
    @DisplayName("A green Koopa Troopa walks faster up a slope and slower down it")
    void aGreenKoopaTroopaFollowsTheSlope() {
        // Prepare
        final KoopaTroopa uphill = koopaTroopaOnTheRamp(GREEN, true);
        final KoopaTroopa downhill = koopaTroopaOnTheRamp(GREEN, false);

        // Execute
        final List<Integer> uphillIntervals = walkFrameIntervals(uphill, steepRamp, uphill::getWalkFrameIndex);
        final List<Integer> downhillIntervals = walkFrameIntervals(downhill, steepRamp, downhill::getWalkFrameIndex);

        // Verify
        assertThat(uphillIntervals).as("Uphill").isNotEmpty().allMatch(List.of(4, 5)::contains);
        assertThat(downhillIntervals).as("Downhill").isNotEmpty().allMatch(interval -> interval == 16);
    }

    @Test
    @DisplayName("A red Koopa Troopa keeps changing walk frame every 8 ticks on a slope, either way")
    void aRedKoopaTroopaKeepsItsPace() {
        // Prepare
        final KoopaTroopa uphill = koopaTroopaOnTheRamp(RED, true);
        final KoopaTroopa downhill = koopaTroopaOnTheRamp(RED, false);

        // Execute
        final List<Integer> uphillIntervals = walkFrameIntervals(uphill, steepRamp, uphill::getWalkFrameIndex);
        final List<Integer> downhillIntervals = walkFrameIntervals(downhill, steepRamp, downhill::getWalkFrameIndex);

        // Verify
        assertThat(uphillIntervals).as("Uphill").isNotEmpty().allMatch(interval -> interval == 8);
        assertThat(downhillIntervals).as("Downhill").isNotEmpty().allMatch(interval -> interval == 8);
    }

    @Test
    @DisplayName("A Para-Goomba keeps changing walk frame every 8 ticks on a slope")
    void aParaGoombaKeepsItsPace() {
        // Prepare
        final ParaGoomba paraGoomba = new ParaGoomba(engineFor(steepRamp), of(0, 0));
        paraGoomba.init();
        paraGoomba.setPosition(rampStart(steepRamp, true));
        paraGoomba.setFacingRight(true);

        // Execute
        final List<Integer> changes = new ArrayList<>();
        int walkFrame = paraGoomba.getWalkFrameIndex();
        for (int tick = 1; tick <= 40; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.getWalkFrameIndex() != walkFrame) {
                walkFrame = paraGoomba.getWalkFrameIndex();
                changes.add(tick);
            }
        }

        // Verify
        assertThat(changes).containsExactly(8, 16, 24, 32, 40);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * @return the ticks between walk frame changes over {@value #TICKS} ticks, leaving out the first change, whose
     *         interval started before the walk
     */
    private List<Integer> walkFrameIntervals(
        final GroundWalkingEnemy enemy,
        final SlopeTerrain terrain,
        final IntSupplier walkFrame
    ) {
        final List<Integer> changeTicks = new ArrayList<>();
        int previousFrame = walkFrame.getAsInt();
        for (int tick = 1; tick <= TICKS; tick++) {
            enemy.motionUpdate();
            final int centreX = (int) Math.floor(enemy.getPosition().x() + BODY_SIZE / 2.0);
            assertThat(isOverTheRamp(terrain, centreX)).as("Tick %d: still on the ramp at x %d", tick, centreX)
                .isTrue();
            if (walkFrame.getAsInt() != previousFrame) {
                previousFrame = walkFrame.getAsInt();
                changeTicks.add(tick);
            }
        }
        final List<Integer> intervals = new ArrayList<>();
        for (int change = 1; change < changeTicks.size(); change++) {
            intervals.add(changeTicks.get(change) - changeTicks.get(change - 1));
        }
        return intervals;
    }

    private boolean isOverTheRamp(
        final SlopeTerrain terrain,
        final int centreX
    ) {
        final int rampWidth = terrain == steepRamp ? 1 : 2;
        return centreX >= FIRST_RAMP_COLUMN * TILE_SPRITE_SIZE
            && centreX < (FIRST_RAMP_COLUMN + RAMP_ROWS * rampWidth) * TILE_SPRITE_SIZE;
    }

    /**
     * @return a spot on the ramp's lowest tile, to walk uphill from, or on its highest tile, to walk downhill from
     */
    private PixelPosition rampStart(
        final SlopeTerrain terrain,
        final boolean uphill
    ) {
        final int rampWidth = terrain == steepRamp ? 1 : 2;
        final int centreX = uphill
            ? FIRST_RAMP_COLUMN * TILE_SPRITE_SIZE + 4
            : (FIRST_RAMP_COLUMN + RAMP_ROWS * rampWidth) * TILE_SPRITE_SIZE - 4;
        return PixelPosition.ofTile(of(0, 0))
            .withX(centreX - BODY_SIZE / 2.0)
            .withY(terrain.surfaceYAt(centreX) - BODY_SIZE);
    }

    private Goomba goombaOnTheRamp(
        final SlopeTerrain terrain,
        final boolean uphill
    ) {
        final Goomba goomba = new Goomba(engineFor(terrain), of(0, 0));
        goomba.init();
        goomba.setPosition(rampStart(terrain, uphill));
        goomba.setFacingRight(uphill);
        return goomba;
    }

    private KoopaTroopa koopaTroopaOnTheRamp(
        final KoopaTroopaMode mode,
        final boolean uphill
    ) {
        final KoopaTroopa troopa = new KoopaTroopa(engineFor(steepRamp), of(0, 0));
        troopa.init();
        troopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, mode.name())));
        troopa.setPosition(rampStart(steepRamp, uphill));
        troopa.setFacingRight(uphill);
        return troopa;
    }

    /**
     * @param step the ramp tiles of one row, left to right
     * @return a ramp of {@value #RAMP_ROWS} rows rising to the right from column {@value #FIRST_RAMP_COLUMN} of the
     *         floor, solid under it and level on top
     */
    private SlopeTerrain risingRamp(final String step) {
        final int columns = FIRST_RAMP_COLUMN + RAMP_ROWS * step.length() + 8;
        final String[] rows = new String[12];
        for (int row = 0; row < 11; row++) {
            final int rampIndex = 10 - row;
            if (rampIndex >= RAMP_ROWS) {
                rows[row] = ".".repeat(columns);
                continue;
            }
            final int rampColumn = FIRST_RAMP_COLUMN + rampIndex * step.length();
            rows[row] = ".".repeat(rampColumn) + step + "#".repeat(columns - rampColumn - step.length());
        }
        rows[11] = "#".repeat(columns);
        return new SlopeTerrain(rows);
    }

    private GameEngine engineFor(final SlopeTerrain terrain) {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(terrain.columns(), terrain.rowCount()));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(terrain.toGrid(gameEngine));
        return gameEngine;
    }
}
