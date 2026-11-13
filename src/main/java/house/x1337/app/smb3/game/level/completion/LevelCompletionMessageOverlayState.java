package house.x1337.app.smb3.game.level.completion;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
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
import house.x1337.app.smb3.enumeration.BoldFontGlyph;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;

/**
 * Shows {@link LevelCompletion#getScreen()} over the level view: a post view with its own camera and one quad, its
 * texture repainted by {@link LevelCompletionMessageRenderer} only when the screen changes. The engine creates it
 * after the foremost-sprite overlay and before the fade, which must darken it. Its node sits outside
 * {@code rootNode}, so it is settled after every change.
 */
@Slf4j
@Prototype
@RequiredArgsConstructor
public class LevelCompletionMessageOverlayState extends BaseAppState implements LevelCompletionMessageRenderer {
    static final String VIEW_NAME = "LevelCompletionMessage";
    private static final String MATERIAL = "Common/MatDefs/Misc/Unshaded.j3md";

    private final LevelCompletion levelCompletion;
    private final Map<BoldFontGlyph, int[]> glyphs = new EnumMap<>(BoldFontGlyph.class);
    private final Map<Integer, int[]> digits = new HashMap<>();
    private final Map<LevelCompletionCardType, int[]> cards = new EnumMap<>(LevelCompletionCardType.class);

    private int[] box;
    private int[] up;
    private int[][] brightDots;
    private int[][] dimDots;
    @Getter(AccessLevel.PACKAGE)
    private Geometry quad;
    @Getter(AccessLevel.PACKAGE)
    private Node node;
    private ViewPort viewPort;
    private RenderManager renderManager;
    private Material material;
    private LevelCompletionScreen shown;

    @Override
    protected void initialize(final Application app) {
        renderManager = app.getRenderManager();
        glyphs.putAll(loadGlyphs());
        box = loadBox();
        digits.putAll(loadDigits());
        up = loadUp();
        brightDots = loadDots(true);
        dimDots = loadDots(false);

        // A unit square seen through a parallel projection over the game's viewport, the way the HUD's camera sees
        // its quad (HeadsUpDisplay#init)
        final Camera camera = app.getCamera().clone();
        camera.setViewPort(0f, 1f, HUD_VIEWPORT_BOTTOM, 1f);
        camera.setParallelProjection(true);
        camera.setFrustum(-1f, 1f, -0.5f, 0.5f, 0.5f, -0.5f);
        camera.setLocation(new Vector3f(0.5f, 0.5f, 0f));
        // Looking down -Z with +Y up, whatever the cloned camera was doing, so +X runs left to right on screen
        camera.lookAtDirection(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);

        material = new Material(app.getAssetManager(), MATERIAL);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        material.getAdditionalRenderState().setDepthWrite(false);

        quad = new Geometry("LevelCompletionMessageQuad", new Quad(1f, 1f));
        quad.setMaterial(material);
        quad.setQueueBucket(Bucket.Transparent);

        node = new Node(VIEW_NAME);
        node.attachChild(quad);

        viewPort = renderManager.createPostView(VIEW_NAME, camera);
        viewPort.setClearFlags(false, false, false);
        viewPort.attachScene(node);

        show(levelCompletion.getScreen());
        log.info("Level completion messages initialised as a post view over the level view");
    }

    @Override
    public int[] glyphPixels(final BoldFontGlyph glyph) {
        return glyphs.get(glyph);
    }

    @Override
    public int[] boxPixels() {
        return box;
    }

    @Override
    public int[] digitPixels(final int lives) {
        return digits.get(lives);
    }

    @Override
    public int[] upPixels() {
        return up;
    }

    @Override
    public int[] dotPixels(
        final boolean bright,
        final int sparkle
    ) {
        return (bright ? brightDots : dimDots)[sparkle];
    }

    // Cached: the lives bonus repaints the view on every tick it moves
    @Override
    public int[] cardPixels(final LevelCompletionCardType card) {
        return cards.computeIfAbsent(card, LevelCompletionMessageRenderer.super::cardPixels);
    }

    @Override
    public void update(final float timePerFrame) {
        show(levelCompletion.getScreen());
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
        // Always on: no message draws nothing
    }

    @Override
    protected void onDisable() {
        // Always on: no message draws nothing
    }

    private void show(final LevelCompletionScreen screen) {
        if (screen.equals(shown)) {
            return;
        }
        shown = screen;
        if (!screen.isEmpty()) {
            final int[] pixels = renderToPixels(screen);
            material.setTexture("ColorMap", loadTexture(pixels, new DimensionsPixels(SCREEN_WIDTH, SCREEN_HEIGHT)));
        }
        quad.setCullHint(screen.isEmpty() ? CullHint.Always : CullHint.Inherit);
        node.updateLogicalState(0f);
        node.updateGeometricState();
    }
}
