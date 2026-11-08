package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.OUT;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.ORIGIN_Y;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.PLANT_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The fire plant hurts and dies as the Piranha Plant does: the same {@code OA3_NOTSTOMPABLE},
 * {@code OAT_BOUNDBOX02} / {@code OAT_BOUNDBOX10} and {@code KILLACT_POOFDEATH} entries for {@code $A4} and
 * {@code $A6} (prg005.asm:174-342), all inherited from {@code PiranhaPlantFamilyEnemy}.
 */
class VenusFireTrapDefeatTest {
    private static final double TOLERANCE = 1e-9;
    private static final int SHELL_KNOCK = 16;

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
    @DisplayName("The boxes are the Piranha Plant's: 12x24 green, 12x20 red, inset 2 px")
    void theBoundBoxesAreThePiranhaPlants() {
        // Prepare
        final VenusFireTrap green = world.plant(GREEN);
        final VenusFireTrap red = world.plant(RED);

        // Execute
        final AxisAlignedBoundingBox greenBox = green.getBounds();
        final AxisAlignedBoundingBox redBox = red.getBounds();

        // Verify
        assertThat(greenBox.left()).isCloseTo(PLANT_X + 2, within(TOLERANCE));
        assertThat(greenBox.right()).isCloseTo(PLANT_X + 14, within(TOLERANCE));
        assertThat(greenBox.top()).isCloseTo(ORIGIN_Y + 2, within(TOLERANCE));
        assertThat(greenBox.bottom()).isCloseTo(ORIGIN_Y + 26, within(TOLERANCE));
        assertThat(redBox.bottom()).isCloseTo(ORIGIN_Y + 22, within(TOLERANCE));
    }

    @Test
    @DisplayName("Every contact hurts, hidden or out, and it cannot be stomped")
    void everyContactHurts() {
        // Prepare
        final VenusFireTrap plant = world.plant(GREEN);
        final LevelScenePlayer player = world.playerAt(PLANT_X, ORIGIN_Y);

        // Execute - hidden, then out
        plant.onPlayerOverlap(player);
        player.getPosition().setX(PLANT_X + 40);
        world.tickUntil(plant, OUT);
        plant.onCollisionFromAbove(player);
        plant.onCollisionFromBelow(player);

        // Verify
        assertThat(plant.canBeStomped()).isFalse();
        assertThat(plant.isHittable()).isTrue();
        verify(player, times(3)).getHurtBy(plant);
        assertThat(plant.isKilled()).isFalse();
    }

    @Test
    @DisplayName("A tail strike on a plant that is out kills it with the flash, the chain score and a poof")
    void aTailStrikeKillsItInAPoof() {
        // Prepare
        final VenusFireTrap plant = world.spawnedPlant(GREEN);
        final LevelScenePlayer player = world.playerAt(PLANT_X - 2 * TILE_SPRITE_SIZE, ORIGIN_Y);
        world.tickUntil(plant, OUT);

        // Execute
        plant.onTailAttack(player);
        plant.motionUpdate();

        // Verify
        assertThat(plant.isKilled()).isTrue();
        assertThat(plant.isHittable()).isFalse();
        assertThat(plant.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(plant.isExpired()).as("Gone the tick after the kill").isTrue();
        verify(world.getTailAttackFlashMotionManager()).spawn(eq(world.getGameEngine()), any(Coordinates.class));
        verify(world.getPoofMotionManager()).spawnPoofDeath(eq(world.getGameEngine()), any());
    }

    @Test
    @DisplayName("A hidden plant ignores the tail and a shell cannot reach it")
    void aHiddenPlantIgnoresTailAndShell() {
        // Prepare
        final VenusFireTrap plant = world.spawnedPlant(GREEN);
        final LevelScenePlayer player = world.playerAt(PLANT_X, ORIGIN_Y);

        // Execute
        plant.onTailAttack(player);
        plant.onStruckByKickedObject(SHELL_KNOCK);

        // Verify
        assertThat(plant.isKilled()).isFalse();
        assertThat(plant.isKilledByRammedObject()).isFalse();
        verify(world.getTailAttackFlashMotionManager(), never()).spawn(any(), any());
        verify(world.getPoofMotionManager(), never()).spawnPoofDeath(any(), any());
    }

    @Test
    @DisplayName("A kicked shell kills a plant that is out in a poof, and leaves the score to the shell")
    void aKickedShellKillsIt() {
        // Prepare
        final VenusFireTrap plant = world.spawnedPlant(RED);
        world.tick(plant, 5);

        // Execute
        assertThat(plant.isKilledByRammedObject()).isTrue();
        plant.onStruckByKickedObject(SHELL_KNOCK);

        // Verify
        assertThat(plant.isKilled()).isTrue();
        assertThat(plant.getPendingScoreReward()).isNull();
        verify(world.getPoofMotionManager()).spawnPoofDeath(eq(world.getGameEngine()), any());
    }

    @Test
    @DisplayName("A killed plant spits nothing more")
    void aKilledPlantSpitsNothing() {
        // Prepare - just short of the World 1 spit tick
        final VenusFireTrap plant = world.plant(GREEN);
        world.playerAt(PLANT_X + 40, ORIGIN_Y);
        world.tickUntil(plant, OUT);
        world.tick(plant, 55);

        // Execute
        plant.onStruckByKickedObject(SHELL_KNOCK);
        plant.motionUpdate();

        // Verify
        assertThat(world.fireballCount()).isZero();
    }
}
