package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireball;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.OUT;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.ORIGIN_Y;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.PLANT_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * When and how the fire plant spits. {@code Piranha_Attack} spits on timer {@code $28} in World 1 and on
 * {@code $40} and {@code $10} elsewhere (prg005.asm:1560-1597); {@code Piranha_SpitFire} aims from
 * {@code (X + 3, Y)} with the near / far speed tables, signed toward the player, and gives up when every
 * slot is taken or a sprite is off screen (prg005.asm:1692-1773, prg000.asm:670-687).
 */
class VenusFireTrapFiringTest {
    private static final double TOLERANCE = 1e-9;
    private static final double GREEN_TOP = ORIGIN_Y - 25;
    private static final double BELOW = ORIGIN_Y;
    private static final double ABOVE = GREEN_TOP - 1;

    private VenusFireTrapTestWorld world;

    @BeforeEach
    void prepare() {
        world = new VenusFireTrapTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // Schedule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("In World 1 it spits once per appearance, 56 ticks after reaching the top")
    void inWorldOneItSpitsOnce() {
        // Prepare
        final VenusFireTrap plant = outPlant(GREEN);

        // Execute
        final List<Integer> spitTicks = spitTicksWhileOut(plant);

        // Verify
        assertThat(spitTicks).containsExactly(56);
    }

    @ParameterizedTest(name = "World {0}")
    @ValueSource(ints = {2, 5, 8})
    @DisplayName("In every other world it spits twice per appearance, 32 and 80 ticks after reaching the top")
    void inOtherWorldsItSpitsTwice(final int worldNumber) {
        // Prepare
        world.getPlayerData().setWorld(worldNumber);
        final VenusFireTrap plant = outPlant(RED);

        // Execute
        final List<Integer> spitTicks = spitTicksWhileOut(plant);

        // Verify
        assertThat(spitTicks).containsExactly(32, 80);
    }

    @Test
    @DisplayName("The spit ticks are the timer values $28 in World 1, and $40 and $10 elsewhere")
    void theSpitTicksAreTheRomTimerValues() {
        // Prepare
        final VenusFireTrap plant = world.plant(GREEN);

        // Execute & Verify
        assertThat(plant.isSpitTick(0x28)).isTrue();
        assertThat(plant.isSpitTick(0x40)).isFalse();
        assertThat(plant.isSpitTick(0x10)).isFalse();
        world.getPlayerData().setWorld(2);
        assertThat(plant.isSpitTick(0x28)).isFalse();
        assertThat(plant.isSpitTick(0x40)).isTrue();
        assertThat(plant.isSpitTick(0x10)).isTrue();
    }

    // -------------------------------------------------------------------------
    // Position and aim
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The fireball starts at the plant's X + 3 and the top of its head, with no fraction")
    void theFireballStartsAtXPlusThree() {
        // Prepare
        final VenusFireTrap plant = outPlant(GREEN);
        world.playerAt(PLANT_X + 40, BELOW);

        // Execute
        plant.spitFire();

        // Verify
        final PixelPosition start = onlyFireball().getPosition();
        assertThat(start.x()).isCloseTo(PLANT_X + 3, within(TOLERANCE));
        assertThat(start.y()).isCloseTo(GREEN_TOP, within(TOLERANCE));
    }

    @ParameterizedTest(name = "Player dx {0}, {1}: velocity ({2}, {3})")
    @CsvSource({
        // Near: PiranhaFireball_XVel / YVel index 0, $0B / $0B
        "40, below, 11, 11",
        "40, above, 11, -11",
        "-40, below, -11, 11",
        "-40, above, -11, -11",
        // Far: index 1, $0E / $05
        "120, below, 14, 5",
        "120, above, 14, -5",
        "-120, below, -14, 5",
        "-120, above, -14, -5"
    })
    @DisplayName("The fireball heads into the player's quadrant, at the near or far speeds")
    void theFireballHeadsIntoThePlayersQuadrant(
        final int playerDx,
        final String side,
        final int expectedX,
        final int expectedY
    ) {
        // Prepare
        final VenusFireTrap plant = outPlant(GREEN);
        world.playerAt(PLANT_X + playerDx, "above".equals(side) ? ABOVE : BELOW);

        // Execute
        plant.spitFire();

        // Verify
        assertThat(onlyFireball().getVelocity()).isEqualTo(new FixedPointVelocity(expectedX, expectedY));
    }

    @Test
    @DisplayName("Level with the plant's top counts as below, and directly above counts as to the right")
    void tiesAimRightAndDown() {
        // Prepare - Level_ObjCalc*Diffs return 1 only for a negative difference
        final VenusFireTrap plant = outPlant(GREEN);
        world.playerAt(PLANT_X, GREEN_TOP);

        // Execute
        plant.spitFire();

        // Verify
        assertThat(onlyFireball().getVelocity()).isEqualTo(new FixedPointVelocity(11, 11));
    }

    @ParameterizedTest(name = "dx {0}: {1}")
    @CsvSource({"-81, far", "-80, near", "0, near", "79, near", "80, far"})
    @DisplayName("Near is -80 to 79 px, the ROM's (dx + $50) & $FF < $A0")
    void nearIsMinusEightyToSeventyNine(final int playerDx, final String range) {
        // Prepare
        final int expectedSpeed = "near".equals(range) ? 0x0B : 0x0E;

        // Execute
        final FixedPointVelocity velocity = VenusFireTrap.aimAt(playerDx, false);

        // Verify
        assertThat(Math.abs(velocity.x())).isEqualTo(expectedSpeed);
    }

    @Test
    @DisplayName("The near / far boundary holds for a real spit at -80 and -81 px")
    void theBoundaryHoldsForARealSpit() {
        // Prepare
        final VenusFireTrap plant = outPlant(GREEN);
        final LevelScenePlayer player = world.playerAt(PLANT_X - 80, BELOW);

        // Execute
        plant.spitFire();
        player.getPosition().setX(PLANT_X - 81);
        plant.spitFire();

        // Verify - slots fill 3 down to 0
        final List<PiranhaFireball> fireballs = world.getFireballMotionManager().getActiveFireballs();
        assertThat(fireballs).hasSize(2);
        assertThat(fireballs.get(1).getVelocity()).as("Slot 3, near").isEqualTo(new FixedPointVelocity(-11, 11));
        assertThat(fireballs.get(0).getVelocity()).as("Slot 2, far").isEqualTo(new FixedPointVelocity(-14, 5));
    }

    // -------------------------------------------------------------------------
    // Aborts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("With four fireballs already out, a spit creates nothing")
    void withFourFireballsOutASpitCreatesNothing() {
        // Prepare
        final VenusFireTrap plant = outPlant(GREEN);
        world.playerAt(PLANT_X + 40, BELOW);
        for (int fireball = 0; fireball < 4; fireball++) {
            plant.spitFire();
        }
        assertThat(world.fireballCount()).isEqualTo(4);

        // Execute
        plant.spitFire();

        // Verify
        assertThat(world.fireballCount()).isEqualTo(4);
    }

    @ParameterizedTest(name = "View at ({0}, {1}) from the plant: off = {2}")
    @CsvSource({
        // Left: the plant's left column must be on screen
        "0, 0, false",
        "1, 0, true",
        // Right: so must the column at X + 16
        "-239, 0, false",
        "-240, 0, true",
        // Top: the bottom of the head row, Y + 16
        "0, 16, false",
        "0, 17, true",
        // Bottom: the bottom of the stem row, Y + 32, above line 240
        "0, -207, false",
        "0, -208, true"
    })
    @DisplayName("A sprite is off screen exactly as Object_DetermineHorzVis / VertVis decide for a 16x32 object")
    void theOffScreenTestMatchesTheRom(final int viewLeftFromPlant, final int viewTopFromPlant, final boolean off) {
        // Prepare
        final VenusFireTrap plant = outPlant(GREEN);
        world.viewAt(PLANT_X + viewLeftFromPlant, GREEN_TOP + viewTopFromPlant);

        // Execute
        final boolean anyOffScreen = plant.isAnySpriteOffScreen();

        // Verify
        assertThat(anyOffScreen).isEqualTo(off);
    }

    @Test
    @DisplayName("A plant partly off screen lets its spit tick pass without a fireball")
    void aPlantPartlyOffScreenDoesNotSpit() {
        // Prepare - the view's left edge 1 px right of the plant's left column
        final VenusFireTrap plant = outPlant(GREEN);
        world.viewAt(PLANT_X + 1, 0);

        // Execute
        final List<Integer> spitTicks = spitTicksWhileOut(plant);

        // Verify
        assertThat(spitTicks).isEmpty();
        assertThat(world.fireballCount()).isZero();
    }

    /** A plant that has just reached the top, with a player 40 px to its right and below it. */
    private VenusFireTrap outPlant(final PiranhaPlantMode mode) {
        final VenusFireTrap plant = world.plant(mode);
        world.playerAt(PLANT_X + 40, BELOW);
        world.tickUntil(plant, OUT);
        return plant;
    }

    /** @return the ticks after reaching the top on which a fireball appeared, until the plant sinks */
    private List<Integer> spitTicksWhileOut(final VenusFireTrap plant) {
        final List<Integer> spitTicks = new ArrayList<>();
        for (int tick = 1; plant.getState() == OUT; tick++) {
            final int before = world.fireballCount();
            plant.motionUpdate();
            if (world.fireballCount() > before) {
                spitTicks.add(tick);
            }
        }
        return spitTicks;
    }

    private PiranhaFireball onlyFireball() {
        final List<PiranhaFireball> fireballs = world.getFireballMotionManager().getActiveFireballs();
        assertThat(fireballs).hasSize(1);
        return fireballs.getFirst();
    }
}
