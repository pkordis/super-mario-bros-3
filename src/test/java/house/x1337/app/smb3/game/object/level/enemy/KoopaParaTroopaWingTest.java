package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaParaTroopaAnimator;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.enemy.asset.KoopaParaTroopaAnimatorAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_ENEMY_DETAIL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopa.MIDDLE_ROW_CANVAS_TOP;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.REST_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.START_COLUMN;
import static house.x1337.app.smb3.game.object.level.enemy.placement.EnemyDepthAllocator.baseDepthOfSlot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The paratroopa's one wing, from {@code Troopa_Draw} (dasm prg004.asm:4391-4434): on the body column
 * opposite the head, 8 px above the middle body row, flipped with the body, beating off the walk's own
 * {@code Var5}, and drawn in front of the body because it is written to the slots before it.
 */
class KoopaParaTroopaWingTest {
    private static final float TOLERANCE = 1e-6f;
    private static final float BACK_COLUMN = 8 * PIXELS_TO_GAME_UNITS;
    private static final WorldOffset FEET_ANCHOR = WorldOffset.of(5f, 2f, baseDepthOfSlot(0));

    private KoopaParaTroopaTestWorld world;

    @BeforeEach
    void prepare() {
        world = new KoopaParaTroopaTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // The clock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The wing beats 8 ticks up and 8 ticks down")
    void beatsEightUpEightDown() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final List<Integer> beats = new ArrayList<>();
        boolean wingUp = paratroopa.isWingUp();

        // Execute
        for (int tick = 0; tick < 100; tick++) {
            paratroopa.motionUpdate();
            if (paratroopa.isWingUp() != wingUp) {
                wingUp = paratroopa.isWingUp();
                beats.add(tick);
            }
        }

        // Verify - (Var5 + 3) & 4 with Var5 advancing every 2 frames
        assertThat(beats).hasSizeGreaterThan(4);
        for (int beat = 1; beat < beats.size(); beat++) {
            assertThat(beats.get(beat) - beats.get(beat - 1)).as("Beat %d", beat).isEqualTo(8);
        }
    }

    @Test
    @DisplayName("It runs off the walk's own counter: every beat lands 2 ticks after a body frame change")
    void sharesTheWalkCounter() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final List<Integer> frameChanges = new ArrayList<>();
        final List<Integer> beats = new ArrayList<>();
        int frame = paratroopa.getWalkFrameIndex();
        boolean wingUp = paratroopa.isWingUp();

        // Execute
        for (int tick = 0; tick < 100; tick++) {
            paratroopa.motionUpdate();
            if (paratroopa.getWalkFrameIndex() != frame) {
                frame = paratroopa.getWalkFrameIndex();
                frameChanges.add(tick);
            }
            if (paratroopa.isWingUp() != wingUp) {
                wingUp = paratroopa.isWingUp();
                beats.add(tick);
            }
        }

        // Verify - the frame flips when Var5 reaches a multiple of 4, the wing one Var5 step later. Unlike
        // the Para-Goomba's separate Var3, this is a single clock.
        for (final int beat : beats.subList(1, beats.size())) {
            assertThat(frameChanges).as("A body frame change 2 ticks before the beat at %d", beat).contains(beat - 2);
        }
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Facing left, the wing is on the right column, the one opposite the head")
    void facingLeftTheWingIsOnTheRight() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(false);

        // Execute
        final WorldOffset wing = paratroopa.wingDrawOffset(FEET_ANCHOR);

        // Verify
        assertThat(wing.x()).isCloseTo(FEET_ANCHOR.x() + BACK_COLUMN, within(TOLERANCE));
    }

    @Test
    @DisplayName("Facing right, the head swaps sides and the wing is on the left column")
    void facingRightTheWingIsOnTheLeft() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);

        // Execute
        final WorldOffset wing = paratroopa.wingDrawOffset(FEET_ANCHOR);

        // Verify - the anchor is unmirrored, so the left column is the anchor itself
        assertThat(wing.x()).isCloseTo(FEET_ANCHOR.x(), within(TOLERANCE));
    }

    @Test
    @DisplayName("The wing's top is 8 px above the middle body row as each walk frame draws it")
    void theWingHangsOffTheMiddleRow() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final float evenBody = paratroopa.spriteDrawOffset(FEET_ANCHOR).y();
        final float evenWing = paratroopa.wingDrawOffset(FEET_ANCHOR).y();
        while (paratroopa.getWalkFrameIndex() == 0) {
            paratroopa.motionUpdate();
        }

        // Execute
        final float oddBody = paratroopa.spriteDrawOffset(FEET_ANCHOR).y();
        final float oddWing = paratroopa.wingDrawOffset(FEET_ANCHOR).y();

        // Verify - the middle row ($C2/$C4) starts at canvas row 11 in walk_1 and 9 in walk_2, so the 16 px
        // wing spans canvas rows 3..18 and 1..16: 13 and 15 px above each quad's bottom edge
        assertThat(evenWing - evenBody).isCloseTo(13 * PIXELS_TO_GAME_UNITS, within(TOLERANCE));
        assertThat(oddWing - oddBody).isCloseTo(15 * PIXELS_TO_GAME_UNITS, within(TOLERANCE));
        assertThat((oddWing - oddBody) - (evenWing - evenBody))
            .as("Measured in the art, so the wing tracks the middle row rather than the quad")
            .isCloseTo((MIDDLE_ROW_CANVAS_TOP[0] - MIDDLE_ROW_CANVAS_TOP[1]) * PIXELS_TO_GAME_UNITS, within(TOLERANCE));
    }

    @Test
    @DisplayName("The wing is drawn just in front of its own body, at its own depth")
    void theWingIsInFrontOfItsBody() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setBaseDepth(baseDepthOfSlot(9));
        paratroopa.spawnIntoScene();

        // Execute
        world.tick(paratroopa, 3);

        // Verify - Sprite_RAM-$08 is before the body's slots, and the earlier slot wins
        final float body = paratroopa.getSpriteGeometry().getLocalTranslation().z;
        final float wing = paratroopa.getWingGeometry().getLocalTranslation().z;
        assertThat(body).isCloseTo(baseDepthOfSlot(9), within(TOLERANCE));
        assertThat(wing).isCloseTo(baseDepthOfSlot(9) + Z_OFFSET_ENEMY_DETAIL, within(TOLERANCE));
        assertThat(wing - body)
            .as("Close enough that no other enemy fits between them")
            .isLessThan(baseDepthOfSlot(1) - baseDepthOfSlot(0));
    }

    @Test
    @DisplayName("The placed wing quad lands where wingDrawOffset says, for the body's own anchor")
    void thePlacedQuadMatchesTheOffset() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.spawnedParatroopa();

        // Execute
        world.tick(paratroopa, 7);

        // Verify
        final WorldOffset anchor = paratroopa.pixelToWorldOffset(paratroopa.getPosition());
        final WorldOffset expected = paratroopa.wingDrawOffset(anchor);
        final Geometry wing = paratroopa.getWingGeometry();
        assertThat(wing.getLocalTranslation().x).isCloseTo(expected.x(), within(TOLERANCE));
        assertThat(wing.getLocalTranslation().y).isCloseTo(expected.y(), within(TOLERANCE));
        assertThat(wing.getLocalTranslation().z).isCloseTo(expected.z(), within(TOLERANCE));
        assertThat(wing.getLocalScale().x).as("The quad itself is never flipped").isEqualTo(1f);
    }

    // -------------------------------------------------------------------------
    // Which file the animator shows
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Facing left shows the unflipped wing file, facing right the SPR_HFLIP one")
    void theWingFileFollowsTheFacing() {
        // Prepare - a real animator over stand-in textures
        final Texture[] up = {mock(Texture.class), mock(Texture.class)};
        final Texture[] down = {mock(Texture.class), mock(Texture.class)};
        final KoopaParaTroopaAnimator animator = new KoopaParaTroopaAnimator();
        animator.getAssetsByMode().put(GREEN, new KoopaParaTroopaAnimatorAssets(new Texture[2], up, down));
        final Material material = mock(Material.class);
        final Geometry wingGeometry = mock(Geometry.class);
        when(wingGeometry.getMaterial()).thenReturn(material);
        final KoopaParaTroopa paratroopa = mock(KoopaParaTroopa.class);
        when(paratroopa.getMode()).thenReturn(GREEN);
        when(paratroopa.getWingGeometry()).thenReturn(wingGeometry);
        when(paratroopa.isWingUp()).thenReturn(true);

        // Execute & Verify - the wing copies the back body sprite's attribute (prg004.asm:4431-4434)
        when(paratroopa.isFacingRight()).thenReturn(false);
        animator.applyWingFrame(paratroopa);
        verify(material).setTexture("ColorMap", up[KoopaParaTroopaAnimator.UNFLIPPED_WING]);

        when(paratroopa.isFacingRight()).thenReturn(true);
        animator.applyWingFrame(paratroopa);
        verify(material).setTexture("ColorMap", up[KoopaParaTroopaAnimator.FLIPPED_WING]);

        when(paratroopa.isWingUp()).thenReturn(false);
        animator.applyWingFrame(paratroopa);
        verify(material).setTexture("ColorMap", down[KoopaParaTroopaAnimator.FLIPPED_WING]);
    }
}
