package house.x1337.app.smb3.game.object.level.effect;

import house.x1337.app.smb3.game.engine.ForemostSpriteOverlay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The flash manager's part in keeping the overlay alive.
 *
 * <p>The flashes hang off {@link ForemostSpriteOverlay} rather than {@code rootNode}, and the engine only
 * walks its own root — so if this manager stopped pumping the overlay, the sprites would render with stale
 * transforms and survive a level change. Both are silent failures, hence the coverage.
 */
class TailAttackFlashMotionManagerTest {
    private ForemostSpriteOverlay overlay;
    private TailAttackFlashMotionManager manager;

    @BeforeEach
    void prepare() {
        overlay = mock(ForemostSpriteOverlay.class);
        manager = new TailAttackFlashMotionManager(overlay);
    }

    @Test
    @DisplayName("Each tick pushes the overlay's transforms, since nothing else walks that scene")
    void everyTickRefreshesTheOverlay() {
        // Execute
        manager.update();

        // Verify
        verify(overlay).update();
    }

    @Test
    @DisplayName("Ticking with no flash alive is harmless")
    void tickingWithNothingAliveIsHarmless() {
        // Execute
        manager.update();
        manager.update();

        // Verify
        assertThat(manager.getActiveAnimations()).isEmpty();
    }

    @Test
    @DisplayName("A level change clears the overlay, not just the manager's own list")
    void resetAlsoClearsTheOverlay() {
        // Execute
        manager.reset();

        // Verify - the sprites are attached to the overlay node, so emptying the list alone would leak them
        assertThat(manager.getActiveAnimations()).isEmpty();
        verify(overlay).reset();
    }

    @Test
    @DisplayName("It reads its frames from the wham sprite set")
    void itAnimatesTheWhamFrames() {
        assertThat(manager.getAnimationType()).isEqualTo(TailAttackFlashAnimation.class);
    }
}
