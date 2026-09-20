package house.x1337.app.smb3.game.engine;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The overlay that carries sprites which must never be covered by the game scene.
 *
 * <p>It exists because a depth cannot buy that guarantee: within the main scene every sprite shares one
 * material setup with depth testing off, so ordering is the translucent bucket's sort, and the tail-attack
 * flash rendered behind the player at both the largest and the smallest depth in the project. A post view
 * renders after every main viewport has finished instead, which is structural rather than a matter of
 * tuning.
 *
 * <p>The scene living outside {@code rootNode} is the catch, and the reason for most of what is asserted
 * here: nothing else settles it, and jME aborts the frame on a spatial with pending refresh flags.
 */
class ForemostSpriteOverlayTest {
    private RenderManager renderManager;
    private Camera camera;
    private GameEngine gameEngine;
    private ForemostSpriteOverlay overlay;

    @BeforeEach
    void prepare() {
        camera = new Camera(256, 240);
        renderManager = mock(RenderManager.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getRenderManager()).thenReturn(renderManager);
        when(gameEngine.getCamera()).thenReturn(camera);
        when(renderManager.createPostView(eq("ForemostSpriteOverlay"), eq(camera)))
            .thenAnswer(invocation -> new ViewPort(invocation.getArgument(0), invocation.getArgument(1)));
        overlay = new ForemostSpriteOverlay();
    }

    @Test
    @DisplayName("The overlay is a post view, which is what puts it after the whole game scene")
    void theOverlayIsAPostView() {
        // Execute
        overlay.attach(gameEngine, sprite());

        // Verify
        verify(renderManager).createPostView("ForemostSpriteOverlay", camera);
    }

    @Test
    @DisplayName("It shares the game camera, so it scrolls with the level without being synchronised")
    void itSharesTheGameCamera() {
        // Execute
        overlay.attach(gameEngine, sprite());

        // Verify - a clone would have to be kept in step with every scroll and shake
        verify(renderManager).createPostView("ForemostSpriteOverlay", camera);
        verify(gameEngine).getCamera();
    }

    @Test
    @DisplayName("Attaching leaves the scene render-ready, whenever in the frame it happens")
    void attachingLeavesTheSceneRenderReady() {
        // Prepare - a sprite is spawned during collision resolution, which may be after the last pump; jME
        // then aborts the frame with "Scene graph is not properly updated for rendering".
        final Geometry flash = sprite();

        // Execute
        overlay.attach(gameEngine, flash);

        // Verify
        assertRenderReady(overlay.getNode());
    }

    @Test
    @DisplayName("Detaching leaves the scene render-ready too")
    void detachingLeavesTheSceneRenderReady() {
        // Prepare
        final Geometry flash = sprite();
        overlay.attach(gameEngine, flash);

        // Execute
        overlay.detach(flash);

        // Verify
        assertThat(overlay.getNode().getChildren()).isEmpty();
        assertRenderReady(overlay.getNode());
    }

    @Test
    @DisplayName("A sprite's position reaches the renderer, since nothing else walks this scene")
    void aSpritesPositionReachesTheRenderer() {
        // Prepare
        final Geometry flash = sprite();
        flash.setLocalTranslation(3f, 4f, 0f);

        // Execute
        overlay.attach(gameEngine, flash);

        // Verify
        assertThat(flash.getWorldTranslation().getX()).isEqualTo(3f);
        assertThat(flash.getWorldTranslation().getY()).isEqualTo(4f);
    }

    @Test
    @DisplayName("Asking twice reuses the one view rather than stacking up post views")
    void theViewIsCreatedOnlyOnce() {
        // Execute
        overlay.attach(gameEngine, sprite());
        overlay.attach(gameEngine, sprite());

        // Verify
        assertThat(overlay.getNode().getChildren()).hasSize(2);
        verify(renderManager, times(1)).createPostView(eq("ForemostSpriteOverlay"), eq(camera));
    }

    @Test
    @DisplayName("Resetting drops every sprite and leaves the scene render-ready")
    void resetDropsEverySprite() {
        // Prepare
        overlay.attach(gameEngine, sprite());
        overlay.attach(gameEngine, sprite());

        // Execute
        overlay.reset();

        // Verify
        assertThat(overlay.getNode().getChildren()).isEmpty();
        assertRenderReady(overlay.getNode());
    }

    @Test
    @DisplayName("Lifecycle calls before anything is attached are harmless")
    void lifecycleCallsBeforeInitialisationAreSafe() {
        // The engine's managers are ticked from the first frame, long before any flash is struck.
        assertThatCode(() -> {
            overlay.update();
            overlay.reset();
            overlay.detach(sprite());
        }).doesNotThrowAnyException();

        assertThat(overlay.getNode()).isNull();
    }

    @Test
    @DisplayName("Moving a sprite and pumping settles it again")
    void pumpingSettlesAMovedSprite() {
        // Prepare - no overlay sprite moves today, but one that did would dirty the scene on its own tick
        final Geometry flash = sprite();
        overlay.attach(gameEngine, flash);
        flash.setLocalTranslation(9f, 9f, 0f);

        // Execute
        overlay.update();

        // Verify
        assertRenderReady(overlay.getNode());
        assertThat(flash.getWorldTranslation().getX()).isEqualTo(9f);
    }

    @Test
    @DisplayName("A relaunched session gets a fresh view, not the dead engine's orphaned one")
    void aRelaunchedSessionGetsAFreshView() {
        // Prepare - a first session attaches a flash
        final Geometry firstSessionFlash = sprite();
        overlay.attach(gameEngine, firstSessionFlash);
        final Node firstSessionNode = overlay.getNode();

        // Execute - the tester is stopped and launched again, which builds a new engine
        final GameEngine relaunched = relaunchedEngine();
        final Geometry secondSessionFlash = sprite();
        overlay.attach(relaunched, secondSessionFlash);

        // Verify - a node belonging to the previous render manager is never drawn again, so the overlay has
        // to rebuild rather than keep handing out the orphan
        assertThat(overlay.getNode())
            .as("the stale node is discarded")
            .isNotSameAs(firstSessionNode);
        assertThat(overlay.getNode().getChildren())
            .as("only the new session's sprite is present")
            .containsExactly(secondSessionFlash);
        assertRenderReady(overlay.getNode());
    }

    @Test
    @DisplayName("The fresh view is registered with the new engine's render manager and camera")
    void theFreshViewUsesTheNewEnginesRenderManager() {
        // Prepare
        overlay.attach(gameEngine, sprite());

        // Execute
        final GameEngine relaunched = relaunchedEngine();
        overlay.attach(relaunched, sprite());

        // Verify
        verify(relaunched.getRenderManager())
            .createPostView("ForemostSpriteOverlay", relaunched.getCamera());
    }

    @Test
    @DisplayName("Staying within one session keeps the same view")
    void oneSessionKeepsOneView() {
        // Execute
        overlay.attach(gameEngine, sprite());
        final Node node = overlay.getNode();
        overlay.attach(gameEngine, sprite());

        // Verify
        assertThat(overlay.getNode()).isSameAs(node);
        verify(renderManager, times(1)).createPostView(eq("ForemostSpriteOverlay"), eq(camera));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A second session's engine: new render manager, new camera, as {@code @Prototype} scoping gives. */
    private static GameEngine relaunchedEngine() {
        final Camera newCamera = new Camera(256, 240);
        final RenderManager newRenderManager = mock(RenderManager.class);
        when(newRenderManager.createPostView(eq("ForemostSpriteOverlay"), eq(newCamera)))
            .thenAnswer(invocation -> new ViewPort(invocation.getArgument(0), invocation.getArgument(1)));
        final GameEngine engine = mock(GameEngine.class);
        when(engine.getRenderManager()).thenReturn(newRenderManager);
        when(engine.getCamera()).thenReturn(newCamera);
        return engine;
    }

    /**
     * Runs the very check that failed in the crash this design exists to avoid: {@code Spatial#checkCulling}
     * is what {@code RenderManager#renderSubScene} calls, and it throws
     * {@code "Scene graph is not properly updated for rendering"} on a spatial with refresh flags pending.
     *
     * <p>Asserted through jME rather than by inspecting world bounds, which are computed lazily and so look
     * healthy even on an unsettled scene.
     */
    private void assertRenderReady(final Spatial spatial) {
        assertThatCode(() -> checkCullingDeeply(spatial))
            .as("jME must accept the overlay scene for rendering")
            .doesNotThrowAnyException();
    }

    private static void checkCullingDeeply(final Spatial spatial) {
        spatial.checkCulling(wideOpenCamera());
        if (spatial instanceof Node node) {
            for (final Spatial child : node.getChildren()) {
                checkCullingDeeply(child);
            }
        }
    }

    /** A camera whose frustum contains everything, so only the refresh-flag check can fail. */
    private static Camera wideOpenCamera() {
        final Camera wideOpen = new Camera(256, 240);
        wideOpen.setParallelProjection(true);
        wideOpen.setFrustum(-1000f, 1000f, -1000f, 1000f, 1000f, -1000f);
        wideOpen.setLocation(new Vector3f(0f, 0f, 0f));
        return wideOpen;
    }

    private static Geometry sprite() {
        return new Geometry("flash", new Quad(1, 1));
    }
}
