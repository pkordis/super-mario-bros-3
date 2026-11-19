package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.SlopeTerrain;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;

import java.util.Map;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KICKED;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Ground-walking enemies over real ramp tiles: a steep hill (up three tiles, a plateau, down three tiles) and a gentle
 * one.
 */
@TestInstance(PER_CLASS)
class EnemySlopeTest {
    private static final double TOLERANCE = 1e-9;
    private static final int BODY_SIZE = 16;
    private static final int FLOOR_ROW = 11;

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
        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(2);
        final KoopaTroopaAnimator koopaTroopaAnimator = mock(KoopaTroopaAnimator.class);
        when(koopaTroopaAnimator.walkFrameCount(any())).thenReturn(2);
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == GoombaAnimator.class) {
                return goombaAnimator;
            }
            if (type == KoopaTroopaAnimator.class) {
                return koopaTroopaAnimator;
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A Goomba walks over a steep hill on its surface, every frame")
    void aGoombaWalksOverASteepHill() {
        // Prepare
        final Goomba goomba = goombaOn(steepHill, 7, true);

        // Execute & Verify
        for (int frame = 0; frame < 600; frame++) {
            goomba.motionUpdate();
            assertOnTheSurface(goomba, steepHill, frame);
        }
        assertThat(goomba.getPosition().x()).as("Over the hill").isGreaterThan(18 * TILE_SPRITE_SIZE);
        assertThat(goomba.isFacingRight()).as("Never turned back").isTrue();
    }

    @Test
    @DisplayName("A Goomba walks uphill at 5/16 px a frame on a steep slope and 6/16 on a gentle one, downhill at 8/16")
    void aGoombaWalksSlowerUphill() {
        // Prepare
        final Goomba upSteep = goombaOn(steepHill, 11, true);
        final Goomba upGentle = goombaOn(gentleHill, 11, true);
        final Goomba downSteep = goombaOn(steepHill, 11, false);
        settle(upSteep, upGentle, downSteep);
        final double upSteepX = upSteep.getPosition().x();
        final double upGentleX = upGentle.getPosition().x();
        final double downSteepX = downSteep.getPosition().x();

        // Execute
        settle(upSteep, upGentle, downSteep);

        // Verify
        assertThat(upSteep.getPosition().x() - upSteepX).isCloseTo(5 / 16.0, within(TOLERANCE));
        assertThat(upGentle.getPosition().x() - upGentleX).isCloseTo(6 / 16.0, within(TOLERANCE));
        assertThat(downSteepX - downSteep.getPosition().x()).isCloseTo(8 / 16.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A Goomba walking into a solid step with a ramp on top turns back at either side")
    void aGoombaTurnsBackAtAStepUnderARamp() {
        // Prepare
        final Goomba fromTheLeft = goombaOn(stepUnderAHill, 8, true);
        final Goomba fromTheRight = goombaOn(stepUnderAHill, 19, false);

        // Execute
        for (int frame = 0; frame < 120; frame++) {
            fromTheLeft.motionUpdate();
            fromTheRight.motionUpdate();
        }

        // Verify
        assertThat(fromTheLeft.isFacingRight()).as("Turned back from the left side").isFalse();
        assertThat(fromTheLeft.getPosition().x() + BODY_SIZE).isLessThanOrEqualTo(12.0 * TILE_SPRITE_SIZE);
        assertThat(fromTheRight.isFacingRight()).as("Turned back from the right side").isTrue();
        assertThat(fromTheRight.getPosition().x()).isGreaterThanOrEqualTo(16.0 * TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Walking up and down a steep hill, a Goomba is drawn rising and falling as far as it moves")
    void aGoombaIsDrawnGlidingOnASteepHill() {
        // Prepare - the ramps' centre columns: up 160 to 207, down 240 to 287
        final Goomba goomba = goombaOn(steepHill, 7, true);
        int framesOnTheRamps = 0;

        // Execute & Verify
        for (int frame = 0; frame < 600; frame++) {
            final double previousCentreX = goomba.getPosition().x() + BODY_SIZE / 2.0;
            final double previousDrawY = drawY(goomba);
            goomba.motionUpdate();
            final double centreX = goomba.getPosition().x() + BODY_SIZE / 2.0;
            final boolean goingUp = previousCentreX >= 160 && centreX < 207;
            final boolean goingDown = previousCentreX >= 240 && centreX < 287;
            if (goingUp || goingDown) {
                framesOnTheRamps++;
                assertThat(drawY(goomba) - previousDrawY)
                    .as("Frame %d: drawn drop at x %s", frame, goomba.getPosition().x())
                    .isCloseTo(goingUp ? previousCentreX - centreX : centreX - previousCentreX, within(TOLERANCE));
            }
        }
        assertThat(framesOnTheRamps).as("Walked both ramps").isGreaterThan(150);
    }

    @Test
    @DisplayName("Walking up a gentle hill, a Goomba is drawn rising half as far as it moves, across both ramp tiles")
    void aGoombaIsDrawnGlidingUpAGentleHill() {
        // Prepare - the ramps' centre columns: 160 to 224
        final Goomba goomba = goombaOn(gentleHill, 7, true);
        int framesOnTheRamps = 0;

        // Execute & Verify
        for (int frame = 0; frame < 300; frame++) {
            final double previousCentreX = goomba.getPosition().x() + BODY_SIZE / 2.0;
            final double previousDrawY = drawY(goomba);
            goomba.motionUpdate();
            final double centreX = goomba.getPosition().x() + BODY_SIZE / 2.0;
            if (previousCentreX >= 160 && centreX < 224) {
                framesOnTheRamps++;
                assertThat(previousDrawY - drawY(goomba))
                    .as("Frame %d: drawn rise at x %s", frame, goomba.getPosition().x())
                    .isCloseTo((centreX - previousCentreX) / 2, within(TOLERANCE));
            }
        }
        assertThat(framesOnTheRamps).as("Walked the ramps").isGreaterThan(100);
    }

    @Test
    @DisplayName("Off the ramps, nothing is drawn off its position")
    void noDrawOffsetOnTheFlat() {
        // Prepare
        final Goomba goomba = goombaOn(steepHill, 3, true);

        // Execute
        goomba.motionUpdate();

        // Verify
        assertThat(goomba.slopeDrawOffsetPixels(goomba.getPosition())).isZero();
    }

    @Test
    @DisplayName("A red Koopa Troopa walks down a slope instead of turning back at its top")
    void aRedKoopaTroopaWalksDownASlope() {
        // Prepare - on the plateau, heading for the way down
        final KoopaTroopa troopa = new KoopaTroopa(engineFor(steepHill), of(14, 7));
        troopa.init();
        troopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, RED.name())));
        troopa.setFacingRight(true);

        // Execute & Verify
        for (int frame = 0; frame < 160; frame++) {
            troopa.motionUpdate();
            assertOnTheSurface(troopa, steepHill, frame);
        }
        assertThat(troopa.isFacingRight()).as("Never turned back").isTrue();
        assertThat(troopa.getPosition().x()).as("Down on the flat").isGreaterThan(17 * TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("A kicked shell rolls up and over a steep hill on its surface")
    void aKickedShellRollsOverASteepHill() {
        // Prepare
        final KoopaTroopa troopa = new KoopaTroopa(engineFor(steepHill), of(7, FLOOR_ROW - 1));
        troopa.init();
        troopa.enterShell();
        troopa.onKicked(kickingPlayer(), true);
        assertThat(troopa.getState()).isEqualTo(KICKED);

        // Execute & Verify
        for (int frame = 0; frame < 100; frame++) {
            troopa.motionUpdate();
            assertOnTheSurface(troopa, steepHill, frame);
        }
        assertThat(troopa.getPosition().x()).as("Over the hill").isGreaterThan(18 * TILE_SPRITE_SIZE);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void assertOnTheSurface(
        final GroundWalkingEnemy enemy,
        final SlopeTerrain terrain,
        final int frame
    ) {
        final int centreX = (int) Math.floor(enemy.getPosition().x() + BODY_SIZE / 2.0);
        assertThat(enemy.getPosition().y() + BODY_SIZE)
            .as("Frame %d: feet on the surface at x %s", frame, enemy.getPosition().x())
            .isCloseTo(terrain.surfaceYAt(centreX), within(TOLERANCE));
    }

    private double drawY(final Goomba goomba) {
        return goomba.getPosition().y() + goomba.slopeDrawOffsetPixels(goomba.getPosition());
    }

    private void settle(final Goomba... goombas) {
        for (final Goomba goomba : goombas) {
            goomba.motionUpdate();
        }
    }

    private Goomba goombaOn(
        final SlopeTerrain terrain,
        final int column,
        final boolean facingRight
    ) {
        final Goomba goomba = new Goomba(engineFor(terrain), of(column, 0));
        goomba.init();
        goomba.setPosition(goomba.getPosition().withY(terrain.surfaceYAt(column * TILE_SPRITE_SIZE + 8) - BODY_SIZE));
        goomba.setFacingRight(facingRight);
        return goomba;
    }

    private GameEngine engineFor(final SlopeTerrain terrain) {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(terrain.columns(), terrain.rowCount()));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(terrain.toGrid(gameEngine));
        return gameEngine;
    }

    private LevelScenePlayer kickingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setX(6 * TILE_SPRITE_SIZE);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        return player;
    }
}
