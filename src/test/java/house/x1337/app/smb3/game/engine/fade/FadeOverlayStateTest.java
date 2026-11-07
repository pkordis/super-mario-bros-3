package house.x1337.app.smb3.game.engine.fade;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static house.x1337.app.smb3.game.engine.fade.FadeProfile.STANDARD;
import static house.x1337.app.smb3.game.engine.fade.FadeOverlayState.VIEW_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The full-window black quad that shows the fade. Run without a GL context: the render manager is mocked and hands
 * back plain view ports, and render-readiness is asserted through jME's own {@code checkCulling}.
 */
class FadeOverlayStateTest {
    private final FadingEngine fadingEngine = new FadingEngine();
    private final Camera gameCamera = new Camera(256, 240);

    private RenderManager renderManager;
    private FadeOverlayState state;

    @BeforeEach
    void prepare() {
        // The game camera only covers the area above the HUD, like the engine's
        gameCamera.setViewPort(0f, 1f, 0.2f, 1f);
        renderManager = mock(RenderManager.class);
        when(renderManager.createPostView(eq(VIEW_NAME), any(Camera.class)))
            .thenAnswer(invocation -> new ViewPort(invocation.getArgument(0), invocation.getArgument(1)));
        final Application app = mock(Application.class);
        when(app.getRenderManager()).thenReturn(renderManager);
        when(app.getCamera()).thenReturn(gameCamera);
        when(app.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        state = new FadeOverlayState(fadingEngine);
        state.initialize(mock(AppStateManager.class), app);
    }

    @Test
    @DisplayName("The overlay is a post view with its own camera over the whole window, HUD included")
    void isAPostViewOverTheWholeWindow() {
        // Prepare
        final ArgumentCaptor<Camera> camera = ArgumentCaptor.forClass(Camera.class);

        // Verify
        verify(renderManager).createPostView(eq(VIEW_NAME), camera.capture());
        assertThat(camera.getValue()).as("Not the game camera, which stops above the HUD").isNotSameAs(gameCamera);
        assertThat(camera.getValue().getViewPortBottom()).isZero();
        assertThat(camera.getValue().getViewPortTop()).isEqualTo(1f);
        assertThat(camera.getValue().getViewPortLeft()).isZero();
        assertThat(camera.getValue().getViewPortRight()).isEqualTo(1f);
        assertThat(state.getViewPort().isClearDepth()).as("It composites over the finished frame").isFalse();
        assertThat(state.getViewPort().getScenes()).containsExactly(state.getNode());
    }

    @Test
    @DisplayName("With no fade the quad is not drawn")
    void noFadeDrawsNothing() {
        // Verify
        assertThat(state.getQuad().getLocalCullHint()).isEqualTo(CullHint.Always);
        assertRenderReady(state.getNode());
    }

    @Test
    @DisplayName("The quad follows the darkness step by step and stays render-ready")
    void followsTheDarkness() {
        // Prepare
        fadingEngine.fadeOut(STANDARD, null);

        // Execute & Verify
        for (int tick = 1; tick <= 17; tick++) {
            fadingEngine.tick();
            state.update(0.016f);
            final float alpha = colour().a;
            assertThat(alpha).as("Alpha on tick %d", tick).isEqualTo(fadingEngine.getDarkness());
            assertThat(state.getQuad().getLocalCullHint())
                .as("Drawn on tick %d", tick)
                .isEqualTo(alpha > 0f ? CullHint.Inherit : CullHint.Always);
            assertRenderReady(state.getNode());
        }
        assertThat(colour()).as("Black, fully opaque").isEqualTo(new ColorRGBA(0f, 0f, 0f, 1f));
    }

    @Test
    @DisplayName("The quad covers the camera's whole view")
    void quadCoversTheView() {
        // Prepare
        final ArgumentCaptor<Camera> camera = ArgumentCaptor.forClass(Camera.class);
        verify(renderManager).createPostView(eq(VIEW_NAME), camera.capture());

        // Execute
        final Vector3f bottomLeft = camera.getValue().getScreenCoordinates(new Vector3f(0f, 0f, 0f));
        final Vector3f topRight = camera.getValue().getScreenCoordinates(new Vector3f(1f, 1f, 0f));

        // Verify
        assertThat(bottomLeft.x).isCloseTo(0f, offset(0.5f));
        assertThat(bottomLeft.y).isCloseTo(0f, offset(0.5f));
        assertThat(topRight.x).isCloseTo(gameCamera.getWidth(), offset(0.5f));
        assertThat(topRight.y).isCloseTo(gameCamera.getHeight(), offset(0.5f));
    }

    @Test
    @DisplayName("Cleaning up removes the post view")
    void cleanupRemovesTheView() {
        // Prepare
        final ViewPort viewPort = state.getViewPort();

        // Execute
        state.cleanup(mock(Application.class));

        // Verify
        verify(renderManager).removePostView(viewPort);
    }

    private ColorRGBA colour() {
        return (ColorRGBA) state.getQuad().getMaterial().getParam("Color").getValue();
    }

    private static void assertRenderReady(final Spatial spatial) {
        assertThatCode(() -> checkCullingDeeply(spatial))
            .as("jME must accept the overlay scene for rendering")
            .doesNotThrowAnyException();
    }

    private static void checkCullingDeeply(final Spatial spatial) {
        spatial.checkCulling(wideOpenCamera());
        if (spatial instanceof final Node node) {
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
}
