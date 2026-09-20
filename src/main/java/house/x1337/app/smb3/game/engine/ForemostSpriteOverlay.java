package house.x1337.app.smb3.game.engine;

import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A scene rendered after the whole game scene, for the handful of sprites that must never be covered.
 *
 * <p>Within the main scene, what covers what is decided by the {@code Translucent} bucket's back-to-front
 * sort (see {@code GameRenderer#fromTexture}, which also turns depth testing off). That is fine for
 * sprites whose relative order is a matter of taste, but it is not something a sprite can opt out of by
 * choosing a depth: the tail-attack flash was tried at both the largest and the smallest depth in the
 * project and rendered behind the player either way.
 *
 * <p>So the guarantee is taken from jME's own pipeline instead. A post view renders after every main
 * viewport has finished, including its translucent bucket — the same property {@code FrameCaptureState}
 * relies on to read back a completed frame. Anything attached here therefore lands on top of the level,
 * the enemies and the player no matter how the main scene sorted itself.
 *
 * <p>The view shares the game camera rather than a clone, so the overlay scrolls with the level and needs
 * no synchronising, and it clears nothing so it composites over the finished frame.
 *
 * <h2>Outliving the engine</h2>
 *
 * <p>This is a singleton while a {@code GameEngine} is per session, so it can be handed an engine it has
 * never seen: stopping the tester and launching it again builds a fresh engine, with a fresh render manager
 * and camera, while the view and node cached here still belong to the old one. Sprites attached to that
 * orphaned node are simply never drawn. The view is therefore keyed on the render manager that created it
 * and rebuilt whenever a different one turns up, rather than relying on a shutdown hook — the engine can
 * also die without announcing it.
 *
 * <h2>Keeping the scene render-ready</h2>
 *
 * <p>This scene sits outside {@code rootNode}, so the engine's own
 * {@code rootNode.updateGeometricState()} does not reach it, and jME refuses to render a spatial with
 * pending refresh flags — {@code "Scene graph is not properly updated for rendering"}. Every structural
 * change therefore settles the scene immediately, through {@link #attach} and {@link #detach}, rather than
 * waiting for a tick. Ordering is the reason: a sprite can be spawned from anywhere in a frame — the flash
 * is created during collision resolution — which may well be after this overlay was last pumped, and
 * something attached in that window would reach the renderer unsettled.
 */
@Slf4j
@Singleton
public final class ForemostSpriteOverlay {
    @Getter
    private Node node;
    private ViewPort viewPort;
    private RenderManager builtWith;

    /**
     * Attaches an always-on-top sprite, leaving the overlay ready to render.
     *
     * @param gameEngine the running engine
     * @param spatial    the sprite to draw over the game scene
     */
    public void attach(final GameEngine gameEngine, final Spatial spatial) {
        buildViewFor(gameEngine);
        node.attachChild(spatial);
        settle();
    }

    /**
     * Builds the post view, or rebuilds it for a new engine.
     *
     * <p>Deferred rather than done at construction because the render manager and camera only exist once an
     * engine has started, which is long after this singleton is created.
     */
    private void buildViewFor(final GameEngine gameEngine) {
        final RenderManager renderManager = gameEngine.getRenderManager();
        if (node != null && builtWith == renderManager) {
            return;
        }
        // Anything still attached belongs to the previous engine's scene graph and dies with it.
        node = new Node("ForemostSpriteOverlay");
        viewPort = renderManager.createPostView("ForemostSpriteOverlay", gameEngine.getCamera());
        viewPort.setClearFlags(false, false, false);
        viewPort.attachScene(node);
        builtWith = renderManager;
        log.info("Foremost sprite overlay initialised as a post view over the game scene");
    }

    /** Removes a sprite, leaving the overlay ready to render. */
    public void detach(final Spatial spatial) {
        if (node == null) {
            return;
        }
        node.detachChild(spatial);
        settle();
    }

    /**
     * Re-settles the scene after its sprites have moved.
     *
     * <p>Nothing here moves today — the flash is painted where it was struck and stays — but an overlay
     * sprite that animated its position would need this once its tick had run.
     */
    public void update() {
        settle();
    }

    /** Drops every overlay sprite, for a level change or an editor re-run. */
    public void reset() {
        if (node == null) {
            return;
        }
        node.detachAllChildren();
        settle();
    }

    private void settle() {
        if (node != null) {
            node.updateGeometricState();
        }
    }
}
