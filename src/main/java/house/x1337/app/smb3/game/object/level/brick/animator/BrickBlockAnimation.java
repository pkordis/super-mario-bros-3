package house.x1337.app.smb3.game.object.level.brick.animator;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Encapsulates the full lifecycle of a brick-break event: four flying fragments.
 *
 * <p>Coordinate convention: positions in game-units (1 game-unit = 1 tile).
 * jme3 Y increases upward; tile row 0 = top of level.
 */
@Getter
public final class BrickBlockAnimation {

    // -- Physics constants measured from video capture (smb3.avi, 60 FPS) --

    /**
     * Initial upward Y velocity for the upper pair in jme3 game-units/frame.
     * jme3 Y increases upward, so positive = moving up.
     * Video measured: upper pair rises ~53 NES pixels over ~23 frames ≈ 5 px/frame initial.
     * 5 sprite-pixels / TILE_SPRITE_SIZE = 5/16 game-units/frame.
     */
    private static final double UPPER_INIT_Y_VEL = 5.0 / TILE_SPRITE_SIZE;

    /**
     * Initial upward Y velocity for the lower pair.
     * Video measured: lower pair rises only ~10 NES pixels, ~2 px/frame initial.
     */
    private static final double LOWER_INIT_Y_VEL = 2.0 / TILE_SPRITE_SIZE;

    /**
     * Gravity deceleration per frame in jme3 (positive = upward axis).
     * In jme3 upward-Y space, gravity reduces Y velocity each frame.
     * Applied every GRAVITY_INTERVAL frames (dasm: every 4 frames).
     * Video: upper pair decelerates from +5 to 0 over ~20 frames → ~0.25 px/frame².
     * Approximated as 1 NES sprite-pixel per GRAVITY_INTERVAL frames = 1/(16*4) gu/frame.
     */
    private static final double GRAVITY_STEP = -1.0 / TILE_SPRITE_SIZE;

    /**
     * Gravity applied every 4 frames (dasm: {@code Counter_1 AND #$03 == 0}).
     */
    private static final int GRAVITY_INTERVAL = 4;

    /**
     * X separation grows by 1 sprite-pixel per frame.
     * Video: fragments spread ~2 px/frame total → 1 px each side per frame.
     */
    private static final double ONE_PIXEL = 1.0 / TILE_SPRITE_SIZE;

    /**
     * Fragment sprite: 8 sprite-pixels wide × 16 tall.
     */
    private static final float FRAG_W = 8.0f / TILE_SPRITE_SIZE;
    private static final float FRAG_H = 16.0f / TILE_SPRITE_SIZE;

    /**
     * Z for fragments — in front of the player (FOREGROUND = 0.1) to overlay Mario,
     * matching the NES where debris sprites render above the player sprite.
     */
    private static final float FRAGMENT_Z = 0.11f;

    /**
     * Flip period: the video shows a vertical-only flip alternating every 2 frames
     * (orientation A for frames 0-1, orientation B for frames 2-3, repeat).
     * Period = 4 ticks total (2 per orientation).
     */
    private static final int FLIP_PERIOD = 4;

    private static final String FRAGMENT_ASSET = "sprites/object/brick/fragment.png";

    // -- Position fields ---------------------------------------------------

    private final Offset offset;

    /**
     * Bottom-left world X of the tile: {@code offset.x()} (1 tile = 1 game-unit).
     */
    private final float worldX;

    /**
     * Bottom-left world Y of the tile.
     * jme3 Y increases upward; tile row 0 is the top of the level.
     * {@code worldY = dimensions.rows() − 1 − offset.y()}.
     */
    private final float worldY;

    // -- State -------------------------------------------------------------

    private final Node rootNode;

    private double upperYVel;
    private double lowerYVel;

    /**
     * X separation in game-units, grows by ONE_PIXEL each frame.
     */
    private double xDist;

    /**
     * Both pairs start at the same world Y = top of tile (worldY + 1).
     * They diverge because they have different initial Y velocities.
     */
    private double upperPairY;
    private double lowerPairY;

    /**
     * Tick counter for gravity timing and flip cycling.
     */
    private int tick;

    /**
     * Bitmask: bits 0-3 set when fragment [UL, UR, LL, LR] has left the screen.
     */
    private int hiddenMask;

    /**
     * [0]=UL, [1]=UR, [2]=LL, [3]=LR.
     */
    private final Geometry[] fragmentGeometries = new Geometry[4];

    // ---------------------------------------------------------------------

    public BrickBlockAnimation(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        this.offset = offset;
        this.worldX = offset.x();
        this.worldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y();
        this.rootNode = gameEngine.getRootNode();

        // All 4 fragments spawn at the same Y: top edge of the tile.
        // worldY = bottom edge of tile = dimensions.rows() - 1 - offset.y().
        // Top edge = worldY + 1.
        upperPairY = worldY + 1.0;
        lowerPairY = worldY + 1.0;

        upperYVel = UPPER_INIT_Y_VEL;
        lowerYVel = LOWER_INIT_Y_VEL;
        xDist = 0.0;
        tick = 0;
        hiddenMask = 0;

        final Texture texture = loadTexture(gameEngine.getAssetManager(), FRAGMENT_ASSET);
        for (int i = 0; i < 4; i++) {
            fragmentGeometries[i] = buildQuad(gameEngine.getAssetManager(), texture);
            // Always disable face culling — fragments can be V-flipped (scale -Y)
            // which reverses the winding order, so we must render both sides.
            fragmentGeometries[i].getMaterial()
                .getAdditionalRenderState()
                .setFaceCullMode(Off);
        }
        positionAllFragments();
        for (final Geometry fragmentGeometry : fragmentGeometries) {
            rootNode.attachChild(fragmentGeometry);
        }
    }

    public void tick() {
        tick++;

        // Gravity: decrement both Y velocities every GRAVITY_INTERVAL frames.
        // GRAVITY_STEP is negative, so Y velocity decreases (deceleration upward,
        // then acceleration downward) — correct for jme3's upward-Y axis.
        if (tick % GRAVITY_INTERVAL == 0) {
            upperYVel += GRAVITY_STEP;
            lowerYVel += GRAVITY_STEP;
        }

        upperPairY += upperYVel;
        lowerPairY += lowerYVel;
        xDist += ONE_PIXEL;

        positionAllFragments();
    }

    public boolean isExpired() {
        return hiddenMask == 0x0F;
    }

    public void detach() {
        for (final Geometry g : fragmentGeometries) {
            if (g != null) {
                rootNode.detachChild(g);
            }
        }
    }

    private void positionAllFragments() {
        // Left pieces move left; right pieces start at the right half (+ 0.5 tile)
        // and move further right.
        final double leftX = worldX - xDist;
        final double rightX = worldX + 0.5 + xDist;

        // Flip: video shows a vertical-only flip alternating every 2 ticks.
        // All fragments are in sync. vFlip = true for ticks 2-3, 6-7, 10-11, …
        final boolean vFlip = ((tick % FLIP_PERIOD) / 2) == 1;

        // scaleY: +1 (normal) or -1 (V-flipped). When V-flipped, translate by
        // +FRAG_H to keep the quad in the same world position (negative scale
        // pivots around the quad's local origin at its bottom edge).
        final float sy = vFlip ? -1f : 1f;
        final float vShift = vFlip ? FRAG_H : 0f;

        positionFragment(0, leftX, upperPairY, 1f, sy, vShift, false);  // UL
        positionFragment(1, rightX, upperPairY, -1f, sy, vShift, true); // UR — H-mirrored
        positionFragment(2, leftX, lowerPairY, 1f, sy, vShift, false);  // LL
        positionFragment(3, rightX, lowerPairY, -1f, sy, vShift, true); // LR — H-mirrored

        checkOffScreen(0, upperPairY);
        checkOffScreen(1, upperPairY);
        checkOffScreen(2, lowerPairY);
        checkOffScreen(3, lowerPairY);
    }

    private void positionFragment(
        final int idx,
        final double x,
        final double y,
        final float sx,
        final float sy,
        final float vShift,
        final boolean hFlip
    ) {
        if ((hiddenMask & (1 << idx)) != 0) {
            return;
        }
        // H-flip: negative scaleX; pivot offset = +FRAG_W to keep the quad right-edge-anchored.
        final float hShift = hFlip ? FRAG_W : 0f;
        fragmentGeometries[idx].setLocalTranslation(
            (float) x + hShift,
            (float) y + vShift,
            FRAGMENT_Z
        );
        fragmentGeometries[idx].setLocalScale(sx, sy, 1f);
    }

    private Texture loadTexture(
        final AssetManager assetManager,
        final String path
    ) {
        final Texture texture = assetManager.loadTexture(path);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }

    private void checkOffScreen(final int idx, final double y) {
        if ((hiddenMask & (1 << idx)) != 0) {
            return;
        }
        // Fragment is gone once it falls below world Y = 0 (bottom of level)
        // or climbs more than 20 tiles above its spawn tile (sanity cap).
        if (y < 0.0 || y > worldY + 20.0) {
            hiddenMask |= (1 << idx);
            rootNode.detachChild(fragmentGeometries[idx]);
        }
    }

    private Geometry buildQuad(final AssetManager assetManager, final Texture tex) {
        final Geometry g = new Geometry("BrickFrag", new Quad(FRAG_W, FRAG_H));
        final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", tex);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.getAdditionalRenderState().setDepthWrite(false);
        mat.getAdditionalRenderState().setDepthTest(false);
        g.setMaterial(mat);
        g.setQueueBucket(RenderQueue.Bucket.Translucent);
        return g;
    }
}
