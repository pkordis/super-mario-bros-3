package house.x1337.app.smb3.game.engine.fade;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.annotation.Prototype;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Draws {@link FadingEngine}'s darkness as a black quad over the whole window, game and HUD alike, as a fade takes
 * all 32 palette entries down, the status bar's included.
 *
 * <p>It is a post view, which jME renders after every main view (the game and the HUD), with its own camera covering
 * the full window. Post views render in the order they were created, so the engine creates this one after the
 * foremost-sprite overlay (which it must cover) and before frame capture (which must record it).
 *
 * <p>The node sits outside {@code rootNode}, so it is settled after every change, as {@code ForemostSpriteOverlay}
 * does, or jME refuses to render it. The darkness is read once per render frame and not interpolated: a fade moves in
 * whole steps.
 */
@Slf4j
@Prototype
@RequiredArgsConstructor
public class FadeOverlayState extends BaseAppState {
    static final String VIEW_NAME = "FadeOverlay";
    private static final String MATERIAL = "Common/MatDefs/Misc/Unshaded.j3md";

    private final FadingEngine fadingEngine;
    private final ColorRGBA colour = new ColorRGBA(0f, 0f, 0f, 0f);

    @Getter
    private Node node;
    @Getter
    private Geometry quad;
    @Getter
    private ViewPort viewPort;
    private RenderManager renderManager;
    private Material material;
    private float shownDarkness = -1f;

    @Override
    protected void initialize(final Application app) {
        renderManager = app.getRenderManager();

        // A unit square seen through a parallel projection over the whole window, the way the HUD's camera sees its
        // quad (HeadsUpDisplay#init), only with the full window as its viewport.
        final Camera camera = app.getCamera().clone();
        camera.setViewPort(0f, 1f, 0f, 1f);
        camera.setParallelProjection(true);
        camera.setFrustum(-1f, 1f, -0.5f, 0.5f, 0.5f, -0.5f);
        camera.setLocation(new Vector3f(0.5f, 0.5f, 0f));
        // Looking down -Z with +Y up, whatever the cloned camera was doing, so +X runs left to right on screen
        camera.lookAtDirection(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);

        material = new Material(app.getAssetManager(), MATERIAL);
        material.setColor("Color", colour);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        material.getAdditionalRenderState().setDepthWrite(false);

        quad = new Geometry("FadeOverlayQuad", new Quad(1f, 1f));
        quad.setMaterial(material);
        quad.setQueueBucket(Bucket.Transparent);

        node = new Node(VIEW_NAME);
        node.attachChild(quad);

        viewPort = renderManager.createPostView(VIEW_NAME, camera);
        viewPort.setClearFlags(false, false, false);
        viewPort.attachScene(node);

        show(fadingEngine.getDarkness());
        log.info("Fade overlay initialised as a post view over the whole window");
    }

    @Override
    public void update(final float timePerFrame) {
        show(fadingEngine.getDarkness());
    }

    @Override
    protected void cleanup(final Application app) {
        if (viewPort != null) {
            renderManager.removePostView(viewPort);
            viewPort = null;
        }
    }

    @Override
    protected void onEnable() {
        // Always on: a fade that is not running draws nothing
    }

    @Override
    protected void onDisable() {
        // Always on: a fade that is not running draws nothing
    }

    private void show(final float darkness) {
        if (darkness == shownDarkness) {
            return;
        }
        shownDarkness = darkness;
        colour.a = darkness;
        material.setColor("Color", colour);
        quad.setCullHint(darkness <= 0f ? CullHint.Always : CullHint.Inherit);
        node.updateLogicalState(0f);
        node.updateGeometricState();
    }
}
