package house.x1337.app.smb3.game.object.level.projectile;

import com.jme3.scene.Geometry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_PROJECTILE;
import static house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballTestWorld.ROWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * How a piranha fireball flies and how it is drawn: {@code SObj_Fireball} for {@code SOBJ_PIRANHAFIREBALL}
 * (dasm prg007.asm:5121-5212) adds both 4.4 fixed-point velocities with fractional accumulation and
 * nothing else, and picks {@code Fireball_Patterns[(Var1 >> 2) & 3]} with the H-flip XORed by the sign of
 * {@code XVel}.
 */
class PiranhaFireballFlightTest {
    private static final double TOLERANCE = 1e-9;
    private static final float WORLD_TOLERANCE = 1e-5f;
    private static final List<int[]> VELOCITIES = List.of(
        new int[] {0x0B, 0x05},
        new int[] {-0x0B, -0x05},
        new int[] {-0x0E, 0x0B}
    );

    private PiranhaFireballTestWorld world;

    @BeforeEach
    void prepare() {
        world = new PiranhaFireballTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It moves by its 4.4 velocity with fractions kept, matching SObj_AddXVelFrac pixel for pixel")
    void movesWithFractionalAccumulation() {
        for (final int[] velocity : VELOCITIES) {
            // Prepare
            world.getManager().reset();
            final PiranhaFireball fireball = world.spawn(100, 60, velocity[0], velocity[1]);
            final RomAxis romX = new RomAxis(100, velocity[0]);
            final RomAxis romY = new RomAxis(60, velocity[1]);

            for (int tick = 1; tick <= 40; tick++) {
                // Execute
                world.tick(1);
                romX.tick();
                romY.tick();

                // Verify
                assertThat((int) Math.floor(fireball.getPosition().x())).as("X after tick %d", tick)
                    .isEqualTo(romX.position);
                assertThat((int) Math.floor(fireball.getPosition().y())).as("Y after tick %d", tick)
                    .isEqualTo(romY.position);
            }
            assertThat(fireball.getPosition().x()).isCloseTo(100 + 40 * velocity[0] / 16.0, within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("It flies straight through terrain: no gravity, no terrain probe, the velocity never changes")
    void fliesStraightThroughTerrain() {
        // Prepare
        final PiranhaFireball fireball = world.spawn(100, 60, 0x0B, 0x05);

        // Execute
        world.tick(30);

        // Verify
        assertThat(fireball.getVelocity().x()).isEqualTo(0x0B);
        assertThat(fireball.getVelocity().y()).isEqualTo(0x05);
        verify(world.getGameEngine(), never()).getCollisionGrid();
    }

    @Test
    @DisplayName("Spawned on a tick, it moves on that tick: the manager's work is in postCollision, not update")
    void itMovesOnItsSpawnTick() {
        // Prepare
        final PiranhaFireball fireball = world.spawn(100, 60, 0x10, 0);

        // Execute
        world.getManager().update();

        // Verify
        assertThat(fireball.getPosition().x()).as("Nothing happens in update").isCloseTo(100, within(TOLERANCE));

        // Execute
        world.getManager().postCollision();

        // Verify
        assertThat(fireball.getPosition().x()).isCloseTo(101, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Animation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Moving right it shows $65, $67, $65 rotated, $67 rotated, 4 ticks each")
    void theFramesMovingRight() {
        // Prepare
        final PiranhaFireball fireball = world.spawn(100, 60, 0x0B, 0x05);

        // Execute
        final List<String> frames = framesOver(fireball, 16);

        // Verify - Var1 = 1..16 after each tick
        assertThat(frames).containsExactly(
            "$65", "$65", "$65",
            "$67", "$67", "$67", "$67",
            "$65 HV", "$65 HV", "$65 HV", "$65 HV",
            "$67 HV", "$67 HV", "$67 HV", "$67 HV",
            "$65"
        );
    }

    @Test
    @DisplayName("Moving left the H-flip is inverted, so it spins the other way")
    void theFramesMovingLeft() {
        // Prepare
        final PiranhaFireball fireball = world.spawn(100, 60, -0x0B, 0x05);

        // Execute
        final List<String> frames = framesOver(fireball, 16);

        // Verify
        assertThat(frames).containsExactly(
            "$65 H", "$65 H", "$65 H",
            "$67 H", "$67 H", "$67 H", "$67 H",
            "$65 V", "$65 V", "$65 V", "$65 V",
            "$67 V", "$67 V", "$67 V", "$67 V",
            "$65 H"
        );
    }

    @Test
    @DisplayName("A flip is a negative scale about the quad's corner, so the sprite stays on its own pixels")
    void aFlipStaysInPlace() {
        // Prepare - moving left: frame 0 is H-flipped
        final PiranhaFireball fireball = world.spawn(96, 64, -0x10, 0);

        // Execute
        world.tick(1);

        // Verify - pixel (95, 64): the quad's left edge at 95 / 16, its bottom 16 px lower, dropped 1 px
        final Geometry sprite = fireball.getSpriteGeometry();
        assertThat(sprite.getLocalScale().x).isEqualTo(-1f);
        assertThat(sprite.getLocalScale().y).isEqualTo(1f);
        assertThat(sprite.getLocalTranslation().x)
            .isCloseTo(95f / TILE_SPRITE_SIZE + 0.5f, within(WORLD_TOLERANCE));
        assertThat(sprite.getLocalTranslation().y)
            .isCloseTo(ROWS - (64f + 16f) / TILE_SPRITE_SIZE - 1f / TILE_SPRITE_SIZE, within(WORLD_TOLERANCE));
        assertThat(sprite.getLocalTranslation().z).isEqualTo(Z_DEPTH_PROJECTILE);
    }

    private List<String> framesOver(final PiranhaFireball fireball, final int ticks) {
        final List<String> frames = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            world.tick(1);
            final String pattern = fireball.patternIndex() == 0 ? "$65" : "$67";
            final String flips = (fireball.isFlippedHorizontally() ? "H" : "")
                + (fireball.isFlippedVertically() ? "V" : "");
            frames.add(flips.isEmpty() ? pattern : pattern + " " + flips);
        }
        return frames;
    }

    /** One axis of a special object as the ROM keeps it: a whole pixel and a fractional byte. */
    private static final class RomAxis {
        private final int velocity;
        private int position;
        private int fraction;

        private RomAxis(final int position, final int velocity) {
            this.position = position;
            this.velocity = velocity & 0xFF;
        }

        /** {@code SObj_AddXVelFrac}: low nibble into the fraction, sign-extended high nibble plus carry. */
        private void tick() {
            final int sum = fraction + ((velocity << 4) & 0xFF);
            final int carry = sum >> 8;
            fraction = sum & 0xFF;
            int whole = velocity >> 4;
            if (whole >= 8) {
                whole -= 16;
            }
            position += whole + carry;
        }
    }
}
