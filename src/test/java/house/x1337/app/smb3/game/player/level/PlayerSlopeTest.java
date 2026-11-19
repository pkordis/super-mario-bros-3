package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.collision.SlopeTerrain;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_POWER_SPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_UPHILL_RUN_SPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_UPHILL_WALK_SPEED;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SLIDING;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A real player, ticked through {@code updateFrame} over real ramp tiles: a steep hill (up three tiles, a plateau,
 * down three tiles) and a gentle one.
 */
@TestInstance(PER_CLASS)
class PlayerSlopeTest {
    private static final double TOLERANCE = 1e-9;
    private static final int FEET_Y = 32;
    private static final double WALL_PUSH_OUT_SLACK_PIXELS = 2;

    private final SlopeTerrain steepHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............/##\\........................",
        ".........../####\\.......................",
        "........../######\\......................",
        "########################################"
    );

    private final SlopeTerrain gentleHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............ab##########################",
        "..........ab############################",
        "########################################"
    );

    private final SlopeTerrain stepUnderAHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............/##\\........................",
        "............####........................",
        "########################################"
    );

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Walking right over a steep hill, the player stays on the ground surface every frame")
    void walkingRightOverASteepHillFollowsTheSurface(final PlayerMode mode) {
        // Prepare
        final LevelScenePlayer player = playerOn(steepHill, mode, TILE_SPRITE_SIZE);
        holding(player, HANDLER_RIGHT);

        // Execute & Verify
        walkAndVerifyTheSurface(player, steepHill, 260);
        assertThat(player.getPosition().getX()).as("Over the hill and beyond").isGreaterThan(18 * TILE_SPRITE_SIZE);
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Running left over a steep hill, the player stays on the ground surface every frame")
    void runningLeftOverASteepHillFollowsTheSurface(final PlayerMode mode) {
        // Prepare
        final LevelScenePlayer player = playerOn(steepHill, mode, 26 * TILE_SPRITE_SIZE);
        holding(player, HANDLER_LEFT, HANDLER_RUN);

        // Execute & Verify
        walkAndVerifyTheSurface(player, steepHill, 200);
        assertThat(player.getPosition().getX()).as("Over the hill and beyond").isLessThan(9 * TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Walking up a gentle hill, the player stays on the ground surface every frame")
    void walkingUpAGentleHillFollowsTheSurface() {
        // Prepare
        final LevelScenePlayer player = playerOn(gentleHill, NORMAL, TILE_SPRITE_SIZE * 7);
        holding(player, HANDLER_RIGHT);

        // Execute & Verify
        walkAndVerifyTheSurface(player, gentleHill, 150);
        assertThat(player.getPosition().getX()).as("Up on the plateau").isGreaterThan(15 * TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Uphill, walking is capped at 13/16 px per frame and running at 22/16, and the P-meter stays empty")
    void uphillSpeedIsCapped() {
        // Prepare
        final LevelScenePlayer walker = playerOn(steepHill, NORMAL, 10 * TILE_SPRITE_SIZE - 6);
        final LevelScenePlayer runner = playerOn(steepHill, NORMAL, 10 * TILE_SPRITE_SIZE - 6);
        holding(walker, HANDLER_RIGHT);
        holding(runner, HANDLER_RIGHT, HANDLER_RUN);

        // Execute & Verify
        double walkerTopSpeed = 0;
        double runnerTopSpeed = 0;
        for (int frame = 0; frame < 40; frame++) {
            walker.updateFrame();
            runner.updateFrame();
            walkerTopSpeed = Math.max(walkerTopSpeed, walker.getPosition().getDX());
            runnerTopSpeed = Math.max(runnerTopSpeed, runner.getPosition().getDX());
            assertThat(runner.getPlayerData().getPlayerPower()).as("Frame %d: no P-meter uphill", frame).isZero();
        }
        assertThat(walkerTopSpeed).as("Walking uphill").isCloseTo(PLAYER_TOP_UPHILL_WALK_SPEED, within(TOLERANCE));
        assertThat(runnerTopSpeed).as("Running uphill").isCloseTo(PLAYER_TOP_UPHILL_RUN_SPEED, within(TOLERANCE));
    }

    @Test
    @DisplayName("Uphill a moving player is pulled back by 3/16 px a frame on top of their speed")
    void theSlopePullsAMovingPlayerDownhill() {
        // Prepare - walking up at a steady 13/16
        final LevelScenePlayer player = playerOn(steepHill, NORMAL, 10 * TILE_SPRITE_SIZE - 6);
        holding(player, HANDLER_RIGHT);
        for (int frame = 0; frame < 30; frame++) {
            player.updateFrame();
        }
        final double startX = player.getPosition().getX();

        // Execute
        player.updateFrame();

        // Verify
        assertThat(player.getPosition().getX() - startX)
            .as("13/16 of speed less the 3/16 pull")
            .isCloseTo(10 / 16.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Running into a steep hill at full power, the player is slowed but never stopped, and stays on it")
    void runningIntoASteepHillAtFullPower() {
        // Prepare
        final LevelScenePlayer player = playerOn(steepHill, NORMAL, 2 * TILE_SPRITE_SIZE);
        player.getPlayerData().setPlayerPower(PMETER_LEVELS);
        player.getPosition().setDX(PLAYER_TOP_POWER_SPEED);
        holding(player, HANDLER_RIGHT, HANDLER_RUN);

        // Execute & Verify
        for (int frame = 0; frame < 120; frame++) {
            player.updateFrame();
            assertThat(player.getPosition().getDX()).as("Frame %d: still moving", frame).isPositive();
            assertThat(player.getRuntimeState().isInAir()).as("Frame %d: on the ground", frame).isFalse();
            assertThat(feetY(player)).as("Frame %d: on the surface", frame)
                .isEqualTo(steepHill.surfaceYAt(centreX(player)));
        }
        assertThat(player.getPosition().getX()).as("Over the hill").isGreaterThan(18 * TILE_SPRITE_SIZE);
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Walking right into a solid step with a ramp on top, the player is stopped at its side")
    void aStepUnderARampIsAWallFromTheLeft(final PlayerMode mode) {
        // Prepare - on the floor, the step's left side at x 192
        final LevelScenePlayer player = playerOn(stepUnderAHill, mode, 8 * TILE_SPRITE_SIZE);
        holding(player, HANDLER_RIGHT);

        // Execute & Verify
        for (int frame = 0; frame < 120; frame++) {
            player.updateFrame();
            assertThat(player.getPosition().getX() + 14)
                .as("Frame %d: the right edge goes no further into the step than the usual wall push-out allows", frame)
                .isLessThanOrEqualTo(12.0 * TILE_SPRITE_SIZE + WALL_PUSH_OUT_SLACK_PIXELS);
            assertThat(player.getRuntimeState().getLowClearanceGrace()).as("Frame %d: no emexit", frame).isZero();
            assertThat(feetY(player)).as("Frame %d: on the floor", frame).isEqualTo(11 * TILE_SPRITE_SIZE);
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Walking left into a solid step with a ramp on top, the player is stopped at its side")
    void aStepUnderARampIsAWallFromTheRight(final PlayerMode mode) {
        // Prepare - on the floor, the step's right side at x 256
        final LevelScenePlayer player = playerOn(stepUnderAHill, mode, 20 * TILE_SPRITE_SIZE);
        holding(player, HANDLER_LEFT);

        // Execute & Verify
        for (int frame = 0; frame < 120; frame++) {
            player.updateFrame();
            assertThat(player.getPosition().getX() + 1)
                .as("Frame %d: the left edge goes no further into the step than the usual wall push-out allows", frame)
                .isGreaterThanOrEqualTo(16.0 * TILE_SPRITE_SIZE - WALL_PUSH_OUT_SLACK_PIXELS);
            assertThat(player.getRuntimeState().getLowClearanceGrace()).as("Frame %d: no emexit", frame).isZero();
            assertThat(feetY(player)).as("Frame %d: on the floor", frame).isEqualTo(11 * TILE_SPRITE_SIZE);
        }
    }

    Stream<Arguments> rampsToGlideUp() {
        return Stream.of(PlayerMode.SHRUNK, NORMAL, PlayerMode.RACCOON).flatMap(mode -> Stream.of(
            // The steep hill's ramps span the centre columns 160 to 208, the gentle hill's 160 to 224
            arguments(mode, "steep", steepHill, 13 * TILE_SPRITE_SIZE, 1.0),
            arguments(mode, "gentle", gentleHill, 14 * TILE_SPRITE_SIZE, 0.5)
        ));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("rampsToGlideUp")
    @DisplayName("Walking up a ramp, the player is drawn on a straight line, across the joins between ramp tiles")
    void walkingUpARampIsDrawnGliding(
        final PlayerMode mode,
        final String name,
        final SlopeTerrain hill,
        final int rampEndX,
        final double risePerPixel
    ) {
        // Prepare - on the floor, heading for the ramps
        final LevelScenePlayer player = playerOn(hill, mode, 7 * TILE_SPRITE_SIZE);
        holding(player, HANDLER_RIGHT);
        final PlayerPosition position = player.getPosition();
        int framesOnTheRamp = 0;

        // Execute & Verify
        for (int frame = 0; frame < 300; frame++) {
            final double previousCentreX = position.getX() + 8;
            final double previousDrawY = position.getDrawY();
            player.updateFrame();
            final double centreX = position.getX() + 8;
            if (previousCentreX >= 10 * TILE_SPRITE_SIZE && centreX < rampEndX) {
                framesOnTheRamp++;
                assertThat(previousDrawY - position.getDrawY())
                    .as("Frame %d: drawn rise at x %s", frame, position.getX())
                    .isCloseTo((centreX - previousCentreX) * risePerPixel, within(TOLERANCE));
            }
        }
        assertThat(framesOnTheRamp).as("Walked the ramp").isGreaterThan(40);
    }
    @Test
    @DisplayName("Standing still on a slope, the player does not slip")
    void aStillPlayerDoesNotSlip() {
        // Prepare
        final LevelScenePlayer player = playerOn(steepHill, NORMAL, 11 * TILE_SPRITE_SIZE);
        player.updateFrame();
        final double startX = player.getPosition().getX();
        final double startY = player.getPosition().getY();

        // Execute
        for (int frame = 0; frame < 30; frame++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getPosition().getX()).isCloseTo(startX, within(TOLERANCE));
        assertThat(player.getPosition().getY()).isCloseTo(startY, within(TOLERANCE));
        assertThat(player.getRuntimeState().isInAir()).isFalse();
    }

    @Test
    @DisplayName("Falling onto a slope, the player lands on its surface")
    void fallingOntoASlopeLandsOnTheSurface() {
        // Prepare - 20 px above the ramp's surface under the centre
        final LevelScenePlayer player = playerOn(steepHill, NORMAL, 11 * TILE_SPRITE_SIZE);
        player.getPosition().setY(player.getPosition().getY() - 20);
        player.getRuntimeState().setTo(FALLING);

        // Execute
        for (int frame = 0; frame < 30 && player.getRuntimeState().isInAir(); frame++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getRuntimeState().isInAir()).as("Landed").isFalse();
        assertThat(feetY(player)).as("On the surface").isEqualTo(steepHill.surfaceYAt(centreX(player)));
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Down on a slope slides: faster and faster downhill, coasting on the flat until it stops")
    void downOnASlopeSlides(final PlayerMode mode) {
        // Prepare - standing on the top of the way down
        final LevelScenePlayer player = playerOn(steepHill, mode, 15 * TILE_SPRITE_SIZE - 6);
        final PlayerRuntimeState runtimeState = player.getRuntimeState();
        player.updateFrame();
        holding(player, HANDLER_DOWN);

        // Execute & Verify
        player.updateFrame();
        assertThat(runtimeState.isSliding()).as("Sliding").isTrue();
        assertThat(runtimeState.getMovement()).isEqualTo(SLIDING);
        assertThat(runtimeState.isDucking()).as("Not ducking").isFalse();
        assertThat(player.getPosition().getDX()).as("The first frame: pull 3, friction 1").isCloseTo(2 / 16.0,
            within(TOLERANCE));

        double previousDX = player.getPosition().getDX();
        while (runtimeState.isOnSlope()) {
            player.updateFrame();
            assertThat(runtimeState.isInAir()).as("Slides on the ground").isFalse();
            if (runtimeState.isOnSlope()) {
                assertThat(player.getPosition().getDX()).as("Speeds up on the way down").isGreaterThan(previousDX);
            }
            previousDX = player.getPosition().getDX();
        }
        assertThat(centreX(player)).as("Down on the flat").isGreaterThanOrEqualTo(18 * TILE_SPRITE_SIZE);
        assertThat(previousDX).as("Fast at the bottom").isGreaterThan(2.0);

        int frames = 0;
        while (runtimeState.isSliding() && frames < 100) {
            player.updateFrame();
            assertThat(player.getPosition().getDX())
                .as("Coasting loses a sixteenth a frame")
                .isCloseTo(previousDX - 1 / 16.0, within(TOLERANCE));
            previousDX = player.getPosition().getDX();
            frames++;
        }
        assertThat(runtimeState.isSliding()).as("Comes to a stop on the flat").isFalse();
        assertThat(runtimeState.getMovement()).as("No longer drawn sliding").isNotEqualTo(SLIDING);
        assertThat(player.getPosition().getDX()).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Holding left or right ends the slide")
    void aDirectionEndsTheSlide() {
        // Prepare
        final LevelScenePlayer player = playerOn(steepHill, NORMAL, 15 * TILE_SPRITE_SIZE - 6);
        player.updateFrame();
        holding(player, HANDLER_DOWN);
        for (int frame = 0; frame < 5; frame++) {
            player.updateFrame();
        }
        assertThat(player.getRuntimeState().isSliding()).isTrue();

        // Execute
        holding(player, HANDLER_DOWN, HANDLER_LEFT);
        player.updateFrame();

        // Verify
        assertThat(player.getRuntimeState().isSliding()).isFalse();
    }

    @Test
    @DisplayName("Down on flat ground still ducks")
    void downOnTheFlatDucks() {
        // Prepare
        final LevelScenePlayer player = playerOn(steepHill, NORMAL, 2 * TILE_SPRITE_SIZE);
        holding(player, HANDLER_DOWN);

        // Execute
        player.updateFrame();
        player.updateFrame();

        // Verify
        assertThat(player.getRuntimeState().isDucking()).isTrue();
        assertThat(player.getRuntimeState().isSliding()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void walkAndVerifyTheSurface(
        final LevelScenePlayer player,
        final SlopeTerrain terrain,
        final int frames
    ) {
        for (int frame = 0; frame < frames; frame++) {
            player.updateFrame();
            assertThat(player.getRuntimeState().isInAir()).as("Frame %d: on the ground", frame).isFalse();
            assertThat(feetY(player))
                .as("Frame %d: feet on the surface at x %s", frame, player.getPosition().getX())
                .isEqualTo(terrain.surfaceYAt(centreX(player)));
        }
    }

    private LevelScenePlayer playerOn(
        final SlopeTerrain terrain,
        final PlayerMode mode,
        final double x
    ) {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(terrain.columns(), terrain.rowCount()));

        final double levelBottom = terrain.rowCount() * (double) TILE_SPRITE_SIZE;
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getPendingRows()).thenReturn(terrain.rowCount());
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(0, levelBottom - 192, 640, levelBottom));
        doCallRealMethod().when(cameraState).isBelowVisibleBottom(any());

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getCollisionGrid()).thenReturn(terrain.toGrid(gameEngine));

        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        final PlayerPosition position = player.getPosition();
        position.setX(x);
        position.setY(terrain.surfaceYAt((int) x + 8) - FEET_Y);
        position.snapshotPrevious();
        return player;
    }

    private void holding(
        final LevelScenePlayer player,
        final String... handlers
    ) {
        final PlayerInputHandler inputHandler = player.getInputHandler();
        for (final String handler : new String[] {HANDLER_LEFT, HANDLER_RIGHT, HANDLER_DOWN, HANDLER_RUN}) {
            when(inputHandler.isActive(handler)).thenReturn(false);
        }
        for (final String handler : handlers) {
            when(inputHandler.isActive(handler)).thenReturn(true);
        }
    }

    private int centreX(final LevelScenePlayer player) {
        return (int) Math.floor(player.getPosition().getX()) + 8;
    }

    private int feetY(final LevelScenePlayer player) {
        return (int) Math.floor(player.getPosition().getY()) + FEET_Y;
    }
}
