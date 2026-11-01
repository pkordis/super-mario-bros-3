package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopa.HOP_Y_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.FLOOR_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.REST_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.REST_ROW_PIXEL_Y;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.START_COLUMN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The hopping green paratroopa's motion: {@code ObjNorm_GroundTroop} (dasm prg004.asm:4001-4150) for
 * {@code $6E}. It walks exactly as the green troopa does, plus two things: {@code YVel = -$30} on every
 * landing (prg004.asm:4131) and {@code DEC YVel} on every airborne frame (prg004.asm:4111).
 */
class KoopaParaTroopaHopTest {
    private static final double TOLERANCE = 1e-9;
    private static final int WALK_SPEED = 8;
    private static final int NET_AIRBORNE_GRAVITY = 2;
    private static final int TERMINAL_FALL = 63;

    /** 24 frames rising from {@code -$30} by 2, one still frame at the apex, 24 frames falling. */
    private static final int HOP_PERIOD_TICKS = 49;

    /** {@code (48 + 46 + ... + 2) / 16} = 37.5 px. */
    private static final double HOP_HEIGHT_PIXELS = 37.5;

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
    // Facing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It faces the nearest player when it spawns and never turns towards the player again")
    void facesThePlayerOnceAtSpawn() {
        // Prepare - the player on the right
        world.playerAtPixelX((START_COLUMN + 6) * (double) TILE_SPRITE_SIZE);
        final KoopaParaTroopa paratroopa = world.spawnedParatroopa();
        assertThat(paratroopa.isFacingRight()).as("ObjInit_GroundTroop faces the player").isTrue();

        // Execute - the player moves to the far left, and the paratroopa keeps hopping for 300 frames
        world.playerAtPixelX(0);
        world.tick(paratroopa, 300);

        // Verify
        assertThat(paratroopa.isFacingRight()).as("ObjNorm_GroundTroop never re-faces").isTrue();
    }

    // -------------------------------------------------------------------------
    // The walk
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It walks at $08 in the direction it faces, half a pixel a frame")
    void walksAtTheTroopasSpeed() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);
        final double startX = paratroopa.getPosition().x();

        // Execute
        world.tick(paratroopa, 10);

        // Verify
        assertThat(paratroopa.getVelocity().x()).isEqualTo(WALK_SPEED);
        assertThat(paratroopa.getPosition().x() - startX).isCloseTo(5.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("It turns round at a wall, flipping its facing only")
    void turnsAtAWall() {
        // Prepare - a wall two tiles to the right
        world.solidColumn(START_COLUMN + 2);
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);

        // Execute
        world.tick(paratroopa, 60);

        // Verify
        assertThat(paratroopa.isFacingRight()).as("Object_FlipFace").isFalse();
        assertThat(paratroopa.getVelocity().x()).as("And the next frame reloads XVel from it").isEqualTo(-WALK_SPEED);
    }

    @Test
    @DisplayName("It turns away from another enemy it meets side-on")
    void turnsAwayFromAnotherEnemy() {
        // Prepare - another walker overlapping its right side
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);
        final double x = paratroopa.getPosition().x();
        final ActiveLevelObject other = mock(ActiveLevelObject.class);
        when(other.getBounds())
            .thenReturn(new AxisAlignedBoundingBox(x + 12, REST_ROW_PIXEL_Y, x + 25, REST_ROW_PIXEL_Y + 8));

        // Execute
        paratroopa.onSideCollisionWith(other);

        // Verify - OAT_BOUNCEOFFOTHERS puts it in the bump pass at all
        assertThat(paratroopa.bouncesOffOtherObjects()).isTrue();
        assertThat(paratroopa.isFacingRight()).as("GroundTroop_BumpOffOthers").isFalse();
    }

    @Test
    @DisplayName("It walks off a ledge rather than turning back, as the green troopa does")
    void walksOffLedges() {
        // Prepare - the floor ends two tiles to the right
        world.openFloorFrom(START_COLUMN + 2);
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);

        // Execute
        world.tick(paratroopa, 200);

        // Verify
        assertThat(paratroopa.getPosition().y())
            .as("Fell past the floor it was hopping on")
            .isGreaterThan(FLOOR_ROW * (double) TILE_SPRITE_SIZE);
        assertThat(paratroopa.isFacingRight()).as("No ledge guard").isTrue();
    }

    // -------------------------------------------------------------------------
    // The hop and the float
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Every landing launches a hop of -$30, with no counter and no cooldown")
    void hopsOnEveryLanding() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final List<Integer> landings = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 5 * HOP_PERIOD_TICKS; tick++) {
            paratroopa.motionUpdate();
            if (paratroopa.isGroundContact()) {
                landings.add(tick);
                assertThat(paratroopa.getVelocity().y())
                    .as("Launched on the landing at tick %d", tick)
                    .isEqualTo(HOP_Y_VELOCITY_FIXED_POINT);
            }
        }

        // Verify
        assertThat(landings).as("It starts on the floor, so the first tick is a landing").startsWith(0);
        for (int hop = 1; hop < landings.size(); hop++) {
            assertThat(landings.get(hop) - landings.get(hop - 1))
                .as("Hop %d takes the same %d frames as every other", hop, HOP_PERIOD_TICKS)
                .isEqualTo(HOP_PERIOD_TICKS);
        }
        assertThat(landings).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("A hop peaks 37.5 px above the floor")
    void aHopPeaksAtThirtySevenAndAHalfPixels() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        double highest = paratroopa.getPosition().y();

        // Execute
        for (int tick = 0; tick < HOP_PERIOD_TICKS; tick++) {
            paratroopa.motionUpdate();
            highest = Math.min(highest, paratroopa.getPosition().y());
        }

        // Verify
        assertThat(REST_ROW_PIXEL_Y - highest).isCloseTo(HOP_HEIGHT_PIXELS, within(TOLERANCE));
    }

    @Test
    @DisplayName("In the air it gains a net +$02 a frame: +$03 gravity less the wing's DEC")
    void floatsAtANetTwoAFrame() {
        // Prepare - launched off the floor
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.motionUpdate();

        // Execute & Verify
        for (int tick = 0; tick < HOP_PERIOD_TICKS - 2; tick++) {
            final int before = paratroopa.getVelocity().y();
            paratroopa.motionUpdate();
            assertThat(paratroopa.isGroundContact()).isFalse();
            assertThat(paratroopa.getVelocity().y() - before)
                .as("Airborne tick %d", tick)
                .isEqualTo(NET_AIRBORNE_GRAVITY);
        }
    }

    @Test
    @DisplayName("Falling from a height it tops out at $3F, because the DEC comes after the $40 cap")
    void fallsAtATerminalOfThreeF() {
        // Prepare - dropped from the top of the level
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, 0);
        int fastest = 0;

        // Execute
        while (!paratroopa.isGroundContact()) {
            paratroopa.motionUpdate();
            if (!paratroopa.isGroundContact()) {
                fastest = Math.max(fastest, paratroopa.getVelocity().y());
            }
        }

        // Verify
        assertThat(fastest).isEqualTo(TERMINAL_FALL);
        assertThat(paratroopa.getPosition().y()).as("And it still lands on the floor").isEqualTo(REST_ROW_PIXEL_Y);
    }

    @Test
    @DisplayName("A ceiling stops the rise: YVel is set to $01 and the float takes it to $00 that frame")
    void aCeilingStopsTheRise() {
        // Prepare - a ceiling two rows above, well inside the 37.5 px hop
        world.solidRow(REST_ROW - 2);
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);

        // Execute
        paratroopa.motionUpdate();
        while (!paratroopa.isCeilingContact()) {
            paratroopa.motionUpdate();
        }

        // Verify - prg004.asm:4093 stores $01, then PRG004_B3E7's DEC runs on the same frame
        assertThat(paratroopa.getPosition().y())
            .as("Held just under the ceiling")
            .isEqualTo((REST_ROW - 1) * (double) TILE_SPRITE_SIZE);
        assertThat(paratroopa.getVelocity().y()).isZero();

        // Execute - the next frame falls
        paratroopa.motionUpdate();

        // Verify
        assertThat(paratroopa.getVelocity().y()).isEqualTo(NET_AIRBORNE_GRAVITY);
    }

    // -------------------------------------------------------------------------
    // The body animation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The body keeps its 8-tick walk cycle in the air, where a plain troopa would freeze")
    void keepsAnimatingInTheAir() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.paratroopaAt(START_COLUMN, REST_ROW);
        final List<Integer> frameChanges = new ArrayList<>();
        int frame = paratroopa.getWalkFrameIndex();
        boolean changedInTheAir = false;

        // Execute
        for (int tick = 0; tick < 3 * HOP_PERIOD_TICKS; tick++) {
            paratroopa.motionUpdate();
            if (paratroopa.getWalkFrameIndex() != frame) {
                frame = paratroopa.getWalkFrameIndex();
                frameChanges.add(tick);
                changedInTheAir |= !paratroopa.isGrounded();
            }
        }

        // Verify - Var5 advances every 2 frames and the frame is (Var5 >> 2) & 1
        assertThat(changedInTheAir).as("The store at prg004.asm:4106 is skipped for $6E").isTrue();
        for (int change = 1; change < frameChanges.size(); change++) {
            assertThat(frameChanges.get(change) - frameChanges.get(change - 1)).isEqualTo(8);
        }
    }
}
