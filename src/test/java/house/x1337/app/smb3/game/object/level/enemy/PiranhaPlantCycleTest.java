package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState;
import house.x1337.app.smb3.game.object.level.enemy.motion.PiranhaPlantMotionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.EMERGING;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.HIDDEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.OUT;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.RETRACTING;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.ORIGIN_Y;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.PIPE_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.PLANT_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The pipe plant's cycle, {@code ObjNorm_Piranha} (dasm prg005.asm:1285-1690): {@code Var4 & 3} picks
 * hide, emerge, attack or retract, every change sets {@code Objects_Timer = $30}, and the object loop runs
 * that timer down before the handler (prg000.asm:1855).
 */
class PiranhaPlantCycleTest {
    private static final double TOLERANCE = 1e-9;
    private static final int TOO_CLOSE = 27;

    /**
     * {tick, state after the tick, pixels above the origin after the tick} for a green plant with no
     * player near. The first rise starts at the origin; the retract stops 1 px short of it, so every later
     * rise is 24 px of movement.
     */
    private static final Object[][] GREEN_CYCLE = {
        {1, EMERGING, 0},
        {2, EMERGING, 1},
        {26, EMERGING, 25},
        {27, OUT, 25},
        {74, OUT, 25},
        {75, RETRACTING, 25},
        {76, RETRACTING, 24},
        {99, RETRACTING, 1},
        {100, HIDDEN, 1},
        {147, HIDDEN, 1},
        {148, EMERGING, 1},
        {172, EMERGING, 25},
        {173, OUT, 25},
        {221, RETRACTING, 25},
        {246, HIDDEN, 1}
    };

    private PiranhaPlantTestWorld world;

    @BeforeEach
    void prepare() {
        world = new PiranhaPlantTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Placed from the stamp's lower-left cell, it spawns hidden in the pipe below, centred on it")
    void spawnsHiddenAndCentred() {
        // Prepare & Execute - the starter is the cell above the pipe's top-left tile
        final PiranhaPlant plant = world.spawnedPlant(GREEN);

        // Verify - the pipe's top-left tile is the ROM object's cell; ObjInit_GreenPiranha: Var5 = Y, X += 8
        assertThat(plant.getOffset().y()).isEqualTo(PIPE_ROW - 1);
        assertThat(plant.getState()).isEqualTo(HIDDEN);
        assertThat(plant.getPosition().x()).isCloseTo(PLANT_X, within(TOLERANCE));
        assertThat(plant.getPosition().y()).isCloseTo(PIPE_ROW * (double) TILE_SPRITE_SIZE, within(TOLERANCE));
        assertThat(plant.getOriginY()).isCloseTo(ORIGIN_Y, within(TOLERANCE));
        assertThat(plant.getStateTimer()).isZero();
    }

    @Test
    @DisplayName("Fully out, a red plant's top is level with the top of its stamp, one tile above the starter")
    void fullyOutItFillsItsStamp() {
        // Prepare
        final PiranhaPlant plant = world.plant(RED);

        // Execute
        world.tick(plant, 35);

        // Verify - drawn 1 px lower than its Y, the 32 px above the pipe are exactly the stamp's two rows
        assertThat(plant.getState()).isEqualTo(OUT);
        assertThat(plant.getPosition().y() + 1)
            .isCloseTo((PIPE_ROW - 2) * (double) TILE_SPRITE_SIZE, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // The cycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The green plant follows the ROM's cycle tick for tick: 25 px up, then 48 out, 24 down, 48 in")
    void theGreenCycleMatchesTheRom() {
        // Prepare
        final PiranhaPlant plant = world.plant(GREEN);
        int tick = 0;

        for (final Object[] checkpoint : GREEN_CYCLE) {
            // Execute
            world.tick(plant, (int) checkpoint[0] - tick);
            tick = (int) checkpoint[0];

            // Verify
            assertThat(plant.getState()).as("State after tick %d", tick).isEqualTo(checkpoint[1]);
            assertThat(ORIGIN_Y - plant.getPosition().y())
                .as("Height after tick %d", tick)
                .isCloseTo((int) checkpoint[2], within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("It moves exactly 1 px a tick, up while emerging and down while retracting")
    void movesOnePixelATick() {
        // Prepare
        final PiranhaPlant plant = world.plant(GREEN);
        final List<Double> steps = new ArrayList<>();

        // Execute
        double previousY = plant.getPosition().y();
        for (int tick = 0; tick < 246; tick++) {
            plant.motionUpdate();
            final double y = plant.getPosition().y();
            if (y != previousY) {
                steps.add(y - previousY);
            }
            previousY = y;
        }

        // Verify - YVel = -$10 / +$10, 1.0 px with no fraction left over
        assertThat(steps).containsOnly(-1.0, 1.0);
        assertThat(steps.stream().filter(step -> step < 0).count()).as("25 up, then 24 up").isEqualTo(49);
    }

    @Test
    @DisplayName("The red plant rises 33 px, with the same speed and waits")
    void theRedPlantRisesHigher() {
        // Prepare
        final PiranhaPlant plant = world.plant(RED);

        // Execute - out on tick 1, then one tick per pixel
        world.tick(plant, 34);

        // Verify
        assertThat(ORIGIN_Y - plant.getPosition().y()).isCloseTo(33, within(TOLERANCE));
        assertThat(plant.getState()).isEqualTo(EMERGING);

        // Execute
        plant.motionUpdate();

        // Verify
        assertThat(plant.getState()).isEqualTo(OUT);
        assertThat(plant.getStateTimer()).isEqualTo(0x30);
    }

    // -------------------------------------------------------------------------
    // The player close by
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("With the player within 27 px either side, a hidden plant whose wait is over stays in")
    void staysInWithThePlayerWithinTwentySevenPixels() {
        for (final int offset : List.of(-TOO_CLOSE, 0, TOO_CLOSE)) {
            // Prepare
            world.playerAtPixelX(PLANT_X + offset);
            final PiranhaPlant plant = world.plant(GREEN);

            // Execute
            world.tick(plant, 200);

            // Verify
            assertThat(plant.getState()).as("Player at %+d px", offset).isEqualTo(HIDDEN);
            assertThat(plant.getPosition().y()).isCloseTo(ORIGIN_Y, within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("It comes out on the first tick the player is 28 px away, on either side")
    void comesOutOnceThePlayerIsTwentyEightPixelsAway() {
        for (final int side : List.of(-1, 1)) {
            // Prepare
            world.playerAtPixelX(PLANT_X + side * TOO_CLOSE);
            final PiranhaPlant plant = world.plant(GREEN);
            world.tick(plant, 60);
            world.playerAtPixelX(PLANT_X + side * (TOO_CLOSE + 1));

            // Execute
            plant.motionUpdate();

            // Verify
            assertThat(plant.getState()).as("Side %d", side).isEqualTo(EMERGING);
        }
    }

    @Test
    @DisplayName("Vertical distance is ignored: a player far above but in line keeps it in")
    void verticalDistanceIsIgnored() {
        // Prepare
        world.playerAtPixelX(PLANT_X).getPosition().setY(0);
        final PiranhaPlant plant = world.plant(GREEN);

        // Execute
        world.tick(plant, 10);

        // Verify
        assertThat(plant.getState()).isEqualTo(HIDDEN);
    }

    @Test
    @DisplayName("A plant already on its way out is not affected by the player coming close")
    void aPlantAlreadyOutIsNotAffected() {
        // Prepare - out on tick 1 with nobody near, then the player walks over the pipe
        final PiranhaPlant plant = world.plant(GREEN);
        plant.motionUpdate();
        world.playerAtPixelX(PLANT_X);
        int tick = 1;

        for (final Object[] checkpoint : GREEN_CYCLE) {
            if ((int) checkpoint[0] > 100) {
                break;
            }
            // Execute
            world.tick(plant, (int) checkpoint[0] - tick);
            tick = (int) checkpoint[0];

            // Verify - the same timings, all the way back into the pipe
            assertThat(plant.getState()).as("State after tick %d", tick).isEqualTo(checkpoint[1]);
        }

        // Execute - but once hidden, it stays there
        world.tick(plant, 100);

        // Verify
        assertThat(plant.getState()).isEqualTo(HIDDEN);
    }

    // -------------------------------------------------------------------------
    // The mouth and the halt
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The mouth alternates every 8 ticks, in every state")
    void theMouthAlternatesEveryEightTicks() {
        // Prepare
        final PiranhaPlant plant = world.plant(GREEN);
        final List<Integer> changes = new ArrayList<>();
        int frame = plant.getHeadFrameIndex();

        // Execute
        for (int tick = 0; tick < 246; tick++) {
            plant.motionUpdate();
            if (plant.getHeadFrameIndex() != frame) {
                frame = plant.getHeadFrameIndex();
                changes.add(tick);
            }
        }

        // Verify - (Var3 >> 3) & 1, read before Var3++
        assertThat(changes).hasSizeGreaterThan(20);
        for (int change = 1; change < changes.size(); change++) {
            assertThat(changes.get(change) - changes.get(change - 1)).as("Change %d", change).isEqualTo(8);
        }
    }

    @Test
    @DisplayName("While gameplay is halted its manager leaves it frozen, timer included")
    void aHaltFreezesIt() {
        // Prepare - mid-way up
        final PiranhaPlant plant = world.plant(GREEN);
        world.tick(plant, 10);
        final PiranhaPlantMotionManager manager = new PiranhaPlantMotionManager();
        manager.spawn(plant);
        final PiranhaPlantState state = plant.getState();
        final double y = plant.getPosition().y();
        final int timer = plant.getStateTimer();
        final int counter = plant.getAnimationCounter();

        // Execute
        for (int tick = 0; tick < 100; tick++) {
            manager.updateWhileHalted();
        }

        // Verify
        assertThat(plant.getState()).isEqualTo(state);
        assertThat(plant.getPosition().y()).isCloseTo(y, within(TOLERANCE));
        assertThat(plant.getStateTimer()).isEqualTo(timer);
        assertThat(plant.getAnimationCounter()).isEqualTo(counter);
    }
}
