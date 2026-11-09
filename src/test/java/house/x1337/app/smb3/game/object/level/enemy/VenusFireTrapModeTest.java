package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.game.object.level.enemy.motion.VenusFireTrapMotionManager;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.VENUS_FIRE_TRAP;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fire plant's registration: "Venus Fire Trap - Green" and "Venus Fire Trap - Red" both build a
 * {@link VenusFireTrap}, which reuses
 * {@code PiranhaPlantMode}: the colours differ only in rise, box and art, as the plain plants do.
 */
class VenusFireTrapModeTest {
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
    @DisplayName("Both colours are placeable from the editor and name the fire plant entity")
    void bothColoursAreRegistered() {
        // Prepare & Execute
        final EnemyType green = EnemyType.VENUS_FIRE_TRAP_GREEN;
        final EnemyType red = EnemyType.VENUS_FIRE_TRAP_RED;

        // Verify
        assertThat(green.getLabel()).isEqualTo("Venus Fire Trap - Green");
        assertThat(green.getLevelObjectType()).isEqualTo(VENUS_FIRE_TRAP);
        assertThat(green.getMode()).isEqualTo(GREEN);
        assertThat(red.getLabel()).isEqualTo("Venus Fire Trap - Red");
        assertThat(red.getLevelObjectType()).isEqualTo(VENUS_FIRE_TRAP);
        assertThat(red.getMode()).isEqualTo(RED);
        assertThat(VENUS_FIRE_TRAP.getInstanceType()).isEqualTo(VenusFireTrap.class);
        assertThat(VENUS_FIRE_TRAP.isMultiTiled()).isTrue();
        assertThat(new VenusFireTrapMotionManager().getType().isInstance(world.plant(GREEN)))
            .as("Its manager claims it, so the spawner can route it")
            .isTrue();
    }

    @Test
    @DisplayName("The modes carry the plain plants' rises, 25 px green and 33 px red, and the doubled wait")
    void theModesCarryTheRisesAndTheDoubledWait() {
        // Prepare & Execute
        final VenusFireTrap green = world.plant(GREEN);
        final VenusFireTrap red = world.plant(RED);

        // Verify
        assertThat(green.getRiseDistancePixels()).isEqualTo(25);
        assertThat(red.getRiseDistancePixels()).isEqualTo(33);
        assertThat(green.getStateWaitTicks()).isEqualTo(96);
        assertThat(red.getStateWaitTicks()).isEqualTo(96);
    }

    @Test
    @DisplayName("An unrecognised mode falls back to green")
    void defaultsToGreen() {
        // Prepare
        final VenusFireTrap plant = world.plant(RED);

        // Execute
        plant.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, "PUCE")));

        // Verify
        assertThat(plant.getMode()).isEqualTo(GREEN);
    }
}
