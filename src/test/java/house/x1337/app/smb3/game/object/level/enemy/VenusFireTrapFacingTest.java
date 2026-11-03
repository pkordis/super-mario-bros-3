package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.OUT;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.ORIGIN_Y;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.PLANT_X;
import static house.x1337.app.smb3.game.object.level.enemy.VenusFireTrapTestWorld.ROWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The head turns toward the player: {@code Piranha_FacePlayerFlip = SPR_HFLIP, $00} from
 * {@code Level_ObjCalcXDiffs}, and {@code Piranha_VFlip = $00, SPR_VFLIP} from {@code Level_ObjCalcYDiffs},
 * written to the two head sprites only (prg005.asm:1282-1283, 1307-1314, 1393-1425). The art faces left and
 * looks down; the stem is never flipped.
 */
class VenusFireTrapFacingTest {
    private static final float TOLERANCE = 1e-5f;
    private static final double GREEN_TOP = ORIGIN_Y - 25;

    private VenusFireTrapTestWorld world;
    private VenusFireTrap plant;
    private LevelScenePlayer player;

    @BeforeEach
    void prepare() {
        world = new VenusFireTrapTestWorld();
        plant = world.spawnedPlant(GREEN);
        player = world.playerAt(PLANT_X + 40, ORIGIN_Y);
        world.tickUntil(plant, OUT);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @ParameterizedTest(name = "Player dx {0}: H-flipped = {1}")
    @CsvSource({"-40, false", "-1, false", "0, true", "1, true", "40, true"})
    @DisplayName("The head faces right, H-flipped, unless the player is to the left")
    void theHeadFacesThePlayerLeftOrRight(final int playerDx, final boolean flipped) {
        // Prepare
        player.getPosition().setX(PLANT_X + playerDx);

        // Execute
        plant.motionUpdate();

        // Verify
        assertThat(plant.isHeadFlippedHorizontally()).isEqualTo(flipped);
    }

    @ParameterizedTest(name = "Player Y top {0}: V-flipped = {1}")
    @CsvSource({"-40, true", "-1, true", "0, false", "1, false", "40, false"})
    @DisplayName("The head looks up, V-flipped, exactly when Player_Y is above the plant's top")
    void theHeadLooksUpExactlyWhenThePlayerIsAbove(final int playerDy, final boolean flipped) {
        // Prepare
        player.getPosition().setY(GREEN_TOP + playerDy);

        // Execute
        plant.motionUpdate();

        // Verify
        assertThat(plant.isHeadFlippedVertically()).isEqualTo(flipped);
    }

    @Test
    @DisplayName("Facing right and looking up mirrors the head quad on both axes in place; the stem stays as drawn")
    void bothFlipsMirrorTheHeadInPlace() {
        // Prepare
        player.getPosition().setX(PLANT_X + 40);
        player.getPosition().setY(GREEN_TOP - 40);

        // Execute
        plant.motionUpdate();

        // Verify - a negative scale about the bottom-left corner, so the corner moves by one quad
        final Geometry head = plant.getSpriteGeometry();
        final Geometry stem = plant.getStemGeometry();
        final float left = (float) (PLANT_X / TILE_SPRITE_SIZE);
        final float headBottom = headBottom();
        assertThat(head.getLocalScale()).isEqualTo(new Vector3f(-1f, -1f, 1f));
        assertThat(head.getLocalTranslation().x).isCloseTo(left + 1f, within(TOLERANCE));
        assertThat(head.getLocalTranslation().y).isCloseTo(headBottom + 1f, within(TOLERANCE));
        assertThat(stem.getLocalScale()).as("The stem never flips").isEqualTo(Vector3f.UNIT_XYZ);
        assertThat(stem.getLocalTranslation().x).isCloseTo(left, within(TOLERANCE));
        assertThat(stem.getLocalTranslation().y).isCloseTo(headBottom - 1f, within(TOLERANCE));
    }

    @Test
    @DisplayName("Facing left and looking down draws the head as the art is")
    void facingLeftAndDownDrawsTheArtAsItIs() {
        // Prepare
        player.getPosition().setX(PLANT_X - 40);
        player.getPosition().setY(ORIGIN_Y);

        // Execute
        plant.motionUpdate();

        // Verify
        final Geometry head = plant.getSpriteGeometry();
        assertThat(head.getLocalScale()).isEqualTo(Vector3f.UNIT_XYZ);
        assertThat(head.getLocalTranslation().x).isCloseTo((float) (PLANT_X / TILE_SPRITE_SIZE), within(TOLERANCE));
        assertThat(head.getLocalTranslation().y).isCloseTo(headBottom(), within(TOLERANCE));
        assertThat(plant.getStemGeometry().getLocalScale()).isEqualTo(Vector3f.UNIT_XYZ);
    }

    /** The head quad's unflipped bottom-left Y: the plant's top, a quad down, drawn 1 px low. */
    private float headBottom() {
        return (float) (ROWS - (plant.getPosition().y() + TILE_SPRITE_SIZE) / TILE_SPRITE_SIZE
            - 1.0 / TILE_SPRITE_SIZE);
    }
}
