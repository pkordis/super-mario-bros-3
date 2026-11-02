package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.game.object.level.enemy.motion.PiranhaPlantMotionManager;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PIRANHA_PLANT;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Piranha Plant's registration: "Piranha Plant - Green" ({@code $A0}) and "Piranha Plant - Red"
 * ({@code $A2}) both build a {@link PiranhaPlant}, which reads its mode in {@code configure}.
 */
class PiranhaPlantModeTest {
    private PiranhaPlantTestWorld world;

    @BeforeEach
    void prepare() {
        world = new PiranhaPlantTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    @DisplayName("Both colours are placeable from the editor and name the plant entity")
    void bothColoursAreRegistered() {
        // Prepare & Execute
        final EnemyType green = EnemyType.PIRANHA_PLANT_GREEN;
        final EnemyType red = EnemyType.PIRANHA_PLANT_RED;

        // Verify
        assertThat(green.getLabel()).isEqualTo("Piranha Plant - Green");
        assertThat(green.getLevelObjectType()).isEqualTo(PIRANHA_PLANT);
        assertThat(green.getMode()).isEqualTo(GREEN);
        assertThat(red.getLabel()).isEqualTo("Piranha Plant - Red");
        assertThat(red.getLevelObjectType()).isEqualTo(PIRANHA_PLANT);
        assertThat(red.getMode()).isEqualTo(RED);
        assertThat(PIRANHA_PLANT.getInstanceType()).isEqualTo(PiranhaPlant.class);
        assertThat(PIRANHA_PLANT.isMultiTiled()).isTrue();
        assertThat(new PiranhaPlantMotionManager().getType().isInstance(world.plant(GREEN)))
            .as("Its manager claims it, so the spawner can route it")
            .isTrue();
    }

    @Test
    @DisplayName("The modes carry the ROM's rise distances: 25 px green, 33 px red")
    void theModesCarryTheRiseDistances() {
        // Prepare & Execute
        final PiranhaPlant green = world.plant(GREEN);
        final PiranhaPlant red = world.plant(RED);

        // Verify - ObjInit_GreenPiranha / ObjInit_RedPiranha's TargetingYVal
        assertThat(green.getRiseDistancePixels()).isEqualTo(25);
        assertThat(red.getRiseDistancePixels()).isEqualTo(33);
    }

    @Test
    @DisplayName("An unrecognised mode falls back to green")
    void defaultsToGreen() {
        // Prepare
        final PiranhaPlant plant = world.plant(RED);

        // Execute
        plant.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, "PUCE")));

        // Verify
        assertThat(plant.getMode()).isEqualTo(GREEN);
    }
}
