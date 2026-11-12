package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.SoundEffect.KOOPA_SHELL;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.OUT;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.ORIGIN_Y;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.PLANT_X;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.ROWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How a Piranha Plant hurts and dies. It cannot be stomped, so every contact hurts; a tail or a shell
 * kills it only while it is out of the pipe, and its death leaves a cloud where it was.
 */
class PiranhaPlantDefeatTest {
    private static final double TOLERANCE = 1e-9;
    private static final float WORLD_TOLERANCE = 1e-5f;
    private static final int SHELL_KNOCK = 16;

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
    // Bounds and hurting
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The green box is 12x24 px and the red one 12x20 px, both from the plant's top")
    void theGreenAndRedBoxesHaveTheirOwnSizes() {
        // Prepare
        final PiranhaPlant green = world.plant(GREEN);
        final PiranhaPlant red = world.plant(RED);

        // Execute
        final AxisAlignedBoundingBox greenBox = green.getBounds();
        final AxisAlignedBoundingBox redBox = red.getBounds();

        // Verify - left 2, width 12, top 2, height 24 / 20
        assertThat(greenBox.left()).isCloseTo(PLANT_X + 2, within(TOLERANCE));
        assertThat(greenBox.right()).isCloseTo(PLANT_X + 14, within(TOLERANCE));
        assertThat(greenBox.top()).isCloseTo(ORIGIN_Y + 2, within(TOLERANCE));
        assertThat(greenBox.bottom()).isCloseTo(ORIGIN_Y + 26, within(TOLERANCE));
        assertThat(redBox.left()).isCloseTo(PLANT_X + 2, within(TOLERANCE));
        assertThat(redBox.right()).isCloseTo(PLANT_X + 14, within(TOLERANCE));
        assertThat(redBox.top()).isCloseTo(ORIGIN_Y + 2, within(TOLERANCE));
        assertThat(redBox.bottom()).as("Shorter, though the red plant rises higher").isCloseTo(
            ORIGIN_Y + 22,
            within(TOLERANCE)
        );
    }

    @Test
    @DisplayName("Every contact hurts the player, from above too, and it cannot be stomped")
    void everyContactHurts() {
        // Prepare
        final PiranhaPlant plant = world.plant(GREEN);
        world.tick(plant, 30);
        final LevelScenePlayer player = world.playerAtPixelX(PLANT_X);

        // Execute
        plant.onCollisionFromAbove(player);
        plant.onCollisionFromBelow(player);
        plant.onPlayerOverlap(player);

        // Verify
        assertThat(plant.canBeStomped()).isFalse();
        verify(player, times(3)).getHurtBy(plant);
        verify(player, never()).nextKillChainReward();
        assertThat(player.getPosition().getDY()).as("No stomp bounce").isZero();
        assertThat(plant.isKilled()).isFalse();
    }

    @Test
    @DisplayName("Hidden, it still hurts on contact and stays in the broad-phase, as the hit check runs in state 0")
    void hiddenItStillHurts() {
        // Prepare
        final PiranhaPlant plant = world.plant(GREEN);
        final LevelScenePlayer player = world.playerAtPixelX(PLANT_X);

        // Execute
        plant.onPlayerOverlap(player);

        // Verify
        assertThat(plant.isHittable()).isTrue();
        verify(player).getHurtBy(plant);
    }

    // -------------------------------------------------------------------------
    // Tail
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A tail strike on a plant that is out kills it with the flash, the chain score and a poof")
    void aTailStrikeKillsItInAPoof() {
        // Prepare - fully out, the player on its left
        final PiranhaPlant plant = world.spawnedPlant(GREEN);
        world.tick(plant, 30);
        assertThat(plant.getState()).isEqualTo(OUT);
        final LevelScenePlayer player = world.playerAtPixelX(PLANT_X - TILE_SPRITE_SIZE);
        final double top = plant.getPosition().y();

        // Execute
        plant.onTailAttack(player);

        // Verify - the kill's chain score, read without advancing it
        assertThat(plant.isKilled()).isTrue();
        assertThat(plant.isDefeated()).isTrue();
        assertThat(plant.isHittable()).as("Out of the broad-phase for good").isFalse();
        assertThat(plant.getPendingScoreReward()).isEqualTo(SCORE_100);
        verify(player, never()).nextKillChainReward();
        verify(world.getTailAttackFlashMotionManager()).spawn(eq(world.getGameEngine()), any(Coordinates.class));

        // Verify - the cloud's 16x16 quad has its top-left at the plant's top-left, in front of the pipe
        final ArgumentCaptor<WorldOffset> cloud = ArgumentCaptor.forClass(WorldOffset.class);
        verify(world.getPoofMotionManager()).spawnPoofDeath(eq(world.getGameEngine()), cloud.capture());
        assertThat(cloud.getValue().x()).isCloseTo((float) (PLANT_X / TILE_SPRITE_SIZE), within(WORLD_TOLERANCE));
        assertThat(cloud.getValue().y()).isCloseTo(
            (float) (ROWS - (top + TILE_SPRITE_SIZE) / TILE_SPRITE_SIZE - 1.0 / TILE_SPRITE_SIZE),
            within(WORLD_TOLERANCE)
        );
        assertThat(cloud.getValue().z()).isEqualTo(Z_DEPTH_POOF);
    }

    @Test
    @DisplayName("A tail strike on a plant that is out plays the kill sound once")
    void aTailStrikePlaysTheKillSound() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);
        world.tick(plant, 30);
        final LevelScenePlayer player = world.playerAtPixelX(PLANT_X - TILE_SPRITE_SIZE);

        // Execute
        plant.onTailAttack(player);

        // Verify
        verify(plant.getSoundPlayer(), times(1)).play(KOOPA_SHELL);
    }

    @Test
    @DisplayName("The tick after the kill the plant is gone, where it was, and it never moves again")
    void theTickAfterTheKillItIsGone() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);
        world.tick(plant, 20);
        plant.onTailAttack(world.playerAtPixelX(0));
        final double y = plant.getPosition().y();

        // Execute
        plant.motionUpdate();

        // Verify - a poof death: the plant is gone and only the cloud remains
        assertThat(plant.isExpired()).isTrue();
        assertThat(plant.getPosition().y()).isCloseTo(y, within(TOLERANCE));
    }

    @Test
    @DisplayName("A hidden plant ignores the tail: being out of sight keeps the tail attack away")
    void aHiddenPlantIgnoresTheTail() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);
        final LevelScenePlayer player = world.playerAtPixelX(PLANT_X);

        // Execute
        plant.onTailAttack(player);

        // Verify
        assertThat(plant.isKilled()).isFalse();
        assertThat(plant.getPendingScoreReward()).isNull();
        verify(world.getTailAttackFlashMotionManager(), never()).spawn(any(), any());
        verify(world.getPoofMotionManager(), never()).spawnPoofDeath(any(), any());
    }

    // -------------------------------------------------------------------------
    // Shells
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A kicked shell kills a plant that is out in a poof, and leaves the score to the shell")
    void aKickedShellKillsIt() {
        // Prepare - on its way up, just out of the pipe
        final PiranhaPlant plant = world.spawnedPlant(RED);
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
    @DisplayName("A shell cannot reach a hidden plant, so it neither kills nor scores it")
    void aShellCannotReachAHiddenPlant() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);

        // Execute
        plant.onStruckByKickedObject(SHELL_KNOCK);

        // Verify - the shell pass skips what isKilledByRammedObject refuses, so no score is paid either
        assertThat(plant.isKilledByRammedObject()).isFalse();
        assertThat(plant.isKilled()).isFalse();
        verify(world.getPoofMotionManager(), never()).spawnPoofDeath(any(), any());
    }

    @Test
    @DisplayName("A carried shell walked into a plant that is out kills it the same way")
    void aCarriedShellKillsIt() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);
        world.tick(plant, 30);
        final LevelScenePlayer holder = world.playerAtPixelX(PLANT_X - 2 * TILE_SPRITE_SIZE);
        final CarriableLevelObject carried = mock(CarriableLevelObject.class);
        when(carried.getCarrier()).thenReturn(holder);
        doCallRealMethod().when(carried).onCarriedInto(any());

        // Execute
        carried.onCarriedInto(plant);

        // Verify
        assertThat(plant.isKilled()).isTrue();
        verify(world.getPoofMotionManager()).spawnPoofDeath(eq(world.getGameEngine()), any());
    }
}
