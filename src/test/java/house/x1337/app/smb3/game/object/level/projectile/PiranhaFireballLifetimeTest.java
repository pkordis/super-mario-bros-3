package house.x1337.app.smb3.game.object.level.projectile;

import house.x1337.app.smb3.model.game.motion.PixelPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a piranha fireball goes away, and how many can exist. {@code SpecialObj_UpdateAndDraw} removes one
 * at {@code X - Horz_Scroll >= 248} or with {@code Y + 16} off the vertical screen (dasm
 * prg007.asm:3122-3152), checked before it moves; the plant may use only special slots 3 down to 0
 * ({@code SpecialObj_FindEmptyAbortY}, prg000.asm:668-687).
 */
class PiranhaFireballLifetimeTest {
    private static final double VIEW_LEFT = 320;
    private static final double VIEW_TOP = 48;

    private PiranhaFireballTestWorld world;

    @BeforeEach
    void prepare() {
        world = new PiranhaFireballTestWorld();
        world.viewAt(VIEW_LEFT, VIEW_TOP);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // Off screen
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Horizontally it lives while 0 <= X - view left < 248, so it goes 8 px before the right edge")
    void theHorizontalLimits() {
        // Execute & Verify
        assertSurvivesATick(VIEW_LEFT, VIEW_TOP + 50, true);
        assertSurvivesATick(VIEW_LEFT - 1, VIEW_TOP + 50, false);
        assertSurvivesATick(VIEW_LEFT + 247, VIEW_TOP + 50, true);
        assertSurvivesATick(VIEW_LEFT + 248, VIEW_TOP + 50, false);
    }

    @Test
    @DisplayName("Vertically it lives while Y + 16 is on the view's screen: 0 <= Y + 16 - view top < 256")
    void theVerticalLimits() {
        // Execute & Verify
        assertSurvivesATick(VIEW_LEFT + 50, VIEW_TOP - 16, true);
        assertSurvivesATick(VIEW_LEFT + 50, VIEW_TOP - 17, false);
        assertSurvivesATick(VIEW_LEFT + 50, VIEW_TOP + 239, true);
        assertSurvivesATick(VIEW_LEFT + 50, VIEW_TOP + 240, false);
    }

    @Test
    @DisplayName("Flying left, it is removed on the tick it starts past the view's left edge")
    void flyingLeftItGoesAtTheEdge() {
        // Prepare - 1 px a tick, starting 2 px in
        final PiranhaFireball fireball = world.spawn(VIEW_LEFT + 2, VIEW_TOP + 50, -0x10, 0);

        // Execute - it moves to +1, then to 0, then to -1
        world.tick(3);

        // Verify
        assertThat(world.getManager().getActiveFireballs()).containsExactly(fireball);

        // Execute - the check comes before the move, so -1 is caught on the next tick
        world.tick(1);

        // Verify
        assertThat(world.getManager().getActiveFireballs()).isEmpty();
        assertThat(fireball.isExpired()).isTrue();
        assertThat(world.getRootNode().getChildren()).isEmpty();
    }

    @Test
    @DisplayName("The removal still runs while gameplay is halted")
    void theRemovalRunsDuringAHalt() {
        // Prepare
        world.spawn(VIEW_LEFT + 100, VIEW_TOP + 50, 0, 0);
        world.viewAt(VIEW_LEFT + 200, VIEW_TOP);

        // Execute
        world.getManager().updateWhileHalted();

        // Verify
        assertThat(world.getManager().getActiveFireballs()).isEmpty();
    }

    @Test
    @DisplayName("A level reset removes every fireball and its sprite")
    void aResetRemovesThemAll() {
        // Prepare
        world.spawn(VIEW_LEFT + 10, VIEW_TOP + 50, 0x0B, 0x05);
        world.spawn(VIEW_LEFT + 20, VIEW_TOP + 50, -0x0B, 0x05);

        // Execute
        world.getManager().reset();

        // Verify
        assertThat(world.getManager().getActiveFireballs()).isEmpty();
        assertThat(world.getRootNode().getChildren()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // The cap
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Four fireballs fill slots 3 down to 0, and a fifth spawn is silently dropped")
    void noMoreThanFour() {
        // Prepare
        final List<Boolean> spawned = new ArrayList<>();

        // Execute
        for (int fireball = 0; fireball < 5; fireball++) {
            spawned.add(world.getManager().spawn(
                world.getGameEngine(),
                new PixelPosition(VIEW_LEFT + 10 * fireball, VIEW_TOP + 50),
                STILL
            ));
        }

        // Verify
        assertThat(spawned).containsExactly(true, true, true, true, false);
        assertThat(world.getManager().getActiveFireballs())
            .extracting(PiranhaFireball::getSlot)
            .containsExactly(0, 1, 2, 3);
        assertThat(world.getRootNode().getChildren()).hasSize(4);
    }

    @Test
    @DisplayName("A slot freed by a removal is taken by the next spawn")
    void aFreedSlotIsReused() {
        // Prepare - slot 3 leaves the screen, slots 2..0 stay
        world.spawn(VIEW_LEFT + 247, VIEW_TOP + 50, 0x10, 0);
        world.spawn(VIEW_LEFT + 10, VIEW_TOP + 50, 0, 0);
        world.spawn(VIEW_LEFT + 20, VIEW_TOP + 50, 0, 0);
        world.spawn(VIEW_LEFT + 30, VIEW_TOP + 50, 0, 0);
        world.tick(2);

        // Execute
        final PiranhaFireball reused = world.spawn(VIEW_LEFT + 40, VIEW_TOP + 50, 0, 0);

        // Verify
        assertThat(reused.getSlot()).isEqualTo(3);
        assertThat(world.getManager().getActiveFireballs()).hasSize(4);
    }

    private void assertSurvivesATick(final double x, final double y, final boolean survives) {
        world.getManager().reset();
        world.spawn(x, y, 0, 0);
        world.tick(1);
        assertThat(world.getManager().getActiveFireballs())
            .as("Fireball at (%s, %s) from the view's corner", x - VIEW_LEFT, y - VIEW_TOP)
            .hasSize(survives ? 1 : 0);
    }
}
