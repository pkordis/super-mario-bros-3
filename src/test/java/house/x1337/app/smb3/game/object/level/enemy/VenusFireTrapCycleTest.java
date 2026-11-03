package house.x1337.app.smb3.game.object.level.enemy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.EMERGING;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.HIDDEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.OUT;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.RETRACTING;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.ORIGIN_Y;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.PLANT_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The fire plant's cycle: the Piranha Plant's, with both waits {@code $60} ticks, since
 * {@code PRG005_A824} doubles {@code $30} for every ID from {@code OBJ_GREENPIRANHA_FIRE} on
 * (prg005.asm:1618-1630). While out, {@code Piranha_Attack} clears {@code Var3} every tick, so the mouth
 * holds frame 0 (prg005.asm:1567).
 */
class VenusFireTrapCycleTest {
    private static final double TOLERANCE = 1e-9;
    private static final int FRAME_RUN_TICKS = 8;

    private VenusFireTrapTestWorld world;

    @BeforeEach
    void prepare() {
        world = new VenusFireTrapTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    @DisplayName("Green rises 25 px, out on tick 27, then stays out for 96 ticks")
    void greenRisesTwentyFivePixelsAndStaysOutNinetySixTicks() {
        // Prepare
        final VenusFireTrap plant = world.plant(GREEN);

        // Execute
        final int ticksToTop = world.tickUntil(plant, OUT);
        final double top = plant.getPosition().y();
        final int timerAtTop = plant.getStateTimer();
        final int ticksOut = world.tickUntil(plant, RETRACTING);

        // Verify
        assertThat(ticksToTop).as("Out on its first tick, then 25 px at 1 px a tick").isEqualTo(27);
        assertThat(top).isCloseTo(ORIGIN_Y - 25, within(TOLERANCE));
        assertThat(timerAtTop).isEqualTo(0x60);
        assertThat(ticksOut).isEqualTo(96);
    }

    @Test
    @DisplayName("Red rises 33 px, out on tick 35")
    void redRisesThirtyThreePixels() {
        // Prepare
        final VenusFireTrap plant = world.plant(RED);

        // Execute
        final int ticksToTop = world.tickUntil(plant, OUT);

        // Verify
        assertThat(ticksToTop).isEqualTo(35);
        assertThat(plant.getPosition().y()).isCloseTo(ORIGIN_Y - 33, within(TOLERANCE));
    }

    @Test
    @DisplayName("Back in the pipe, it waits 96 ticks before it comes out again")
    void itWaitsNinetySixTicksHidden() {
        // Prepare
        final VenusFireTrap plant = world.plant(GREEN);
        world.tickUntil(plant, RETRACTING);
        world.tickUntil(plant, HIDDEN);

        // Execute
        final int ticksHidden = world.tickUntil(plant, EMERGING);

        // Verify
        assertThat(ticksHidden).isEqualTo(96);
    }

    @Test
    @DisplayName("While out the head holds frame 0, once the first out tick has shown the frame read before Var3 = 0")
    void whileOutTheHeadHoldsFrameZero() {
        // Prepare - the rise ends on tick 27, where (26 >> 3) & 1 is frame 1
        final VenusFireTrap plant = world.plant(GREEN);
        world.tickUntil(plant, OUT);
        plant.motionUpdate();
        final int firstOutTickFrame = plant.getHeadFrameIndex();
        final List<Integer> frames = new ArrayList<>();

        // Execute
        while (plant.getState() == OUT) {
            plant.motionUpdate();
            frames.add(plant.getHeadFrameIndex());
        }

        // Verify - ObjNorm_Piranha reads the frame before Piranha_Attack clears Var3
        assertThat(firstOutTickFrame).isEqualTo(1);
        assertThat(frames).hasSize(95).containsOnly(0);
    }

    @Test
    @DisplayName("While rising and sinking the head alternates every 8 ticks")
    void whileMovingTheHeadAlternatesEveryEightTicks() {
        // Prepare
        final VenusFireTrap plant = world.plant(GREEN);
        final List<Integer> rising = new ArrayList<>();
        final List<Integer> sinking = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 3 * FRAME_RUN_TICKS; tick++) {
            plant.motionUpdate();
            rising.add(plant.getHeadFrameIndex());
        }
        world.tickUntil(plant, RETRACTING);
        for (int tick = 0; tick < 3 * FRAME_RUN_TICKS; tick++) {
            plant.motionUpdate();
            sinking.add(plant.getHeadFrameIndex());
        }

        // Verify - (Var3 >> 3) & 1, and Var3 starts the sink at 0
        assertThat(rising).containsExactlyElementsOf(eightTickRuns(0, 1, 0));
        assertThat(sinking).containsExactlyElementsOf(eightTickRuns(0, 1, 0));
    }

    @Test
    @DisplayName("motionUpdate needs no scene graph: a plant never spawned into the scene cycles and fires")
    void motionUpdateNeedsNoSceneGraph() {
        // Prepare
        final VenusFireTrap plant = world.plant(GREEN);
        world.playerAt(PLANT_X + 40, ORIGIN_Y);

        // Execute
        world.tickUntil(plant, RETRACTING);
        world.tickUntil(plant, HIDDEN);

        // Verify
        assertThat(plant.getSpriteGeometry()).isNull();
        assertThat(plant.getStemGeometry()).isNull();
        assertThat(world.fireballCount()).isEqualTo(1);
    }

    private static List<Integer> eightTickRuns(final int... frames) {
        final List<Integer> runs = new ArrayList<>();
        for (final int frame : frames) {
            runs.addAll(Collections.nCopies(FRAME_RUN_TICKS, frame));
        }
        return runs;
    }
}
