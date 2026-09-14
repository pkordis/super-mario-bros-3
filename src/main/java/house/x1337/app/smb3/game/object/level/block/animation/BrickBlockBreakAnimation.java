package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_BRICK_BLOCK_FRAGMENT;

// TODO: abstract
@Getter
public final class BrickBlockBreakAnimation implements GameRenderer {
    private static final double UPPER_INIT_Y_VEL = 5.0 / TILE_SPRITE_SIZE;
    private static final double LOWER_INIT_Y_VEL = 2.0 / TILE_SPRITE_SIZE;
    private static final double GRAVITY_STEP = -1.0 / TILE_SPRITE_SIZE;
    private static final int GRAVITY_INTERVAL = 4;
    private static final double ONE_PIXEL = 1.0 / TILE_SPRITE_SIZE;
    private static final Dimensions FRAGMENT_DIMENSIONS = new Dimensions(
        "BrickFragment",
        8.0f / TILE_SPRITE_SIZE,
        16.0f / TILE_SPRITE_SIZE
    );
    private static final int FLIP_PERIOD = 4;
    private static final String FRAGMENT_ASSET = "sprites/object/brick/plain/fragment.png";
    private final Offset offset;


    private final WorldOffset worldOffset;

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

    public BrickBlockBreakAnimation(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        this.offset = offset;
        this.worldOffset = WorldOffset.of(
            offset.x(),
            gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y(),
            0
        );
        this.rootNode = gameEngine.getRootNode();

        // All 4 fragments spawn at the same Y: top edge of the tile.
        // worldY = bottom edge of tile = dimensions.rows() - 1 - offset.y().
        // Top edge = worldY + 1.
        upperPairY = worldOffset.y() + 1.0;
        lowerPairY = worldOffset.y() + 1.0;

        upperYVel = UPPER_INIT_Y_VEL;
        lowerYVel = LOWER_INIT_Y_VEL;
        xDist = 0.0;
        tick = 0;
        hiddenMask = 0;

        final Texture texture = loadTexture(gameEngine.getAssetManager(), FRAGMENT_ASSET);
        for (int i = 0; i < 4; i++) {
            fragmentGeometries[i] = fromTexture(
                gameEngine.getAssetManager(),
                texture,
                FRAGMENT_DIMENSIONS
            );
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
        final double leftX = worldOffset.x() - xDist;
        final double rightX = worldOffset.x() + 0.5 + xDist;

        // Flip: video shows a vertical-only flip alternating every 2 ticks.
        // All fragments are in sync. vFlip = true for ticks 2-3, 6-7, 10-11, …
        final boolean vFlip = ((tick % FLIP_PERIOD) / 2) == 1;

        // scaleY: +1 (normal) or -1 (V-flipped). When V-flipped, translate by
        // +FRAG_H to keep the quad in the same world position (negative scale
        // pivots around the quad's local origin at its bottom edge).
        final float sy = vFlip ? -1f : 1f;
        final float vShift = vFlip ? FRAGMENT_DIMENSIONS.height() : 0f;

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
        final float hShift = hFlip ? FRAGMENT_DIMENSIONS.width() : 0f;
        fragmentGeometries[idx].setLocalTranslation(
            (float) x + hShift,
            (float) y + vShift,
            Z_DEPTH_BRICK_BLOCK_FRAGMENT
        );
        fragmentGeometries[idx].setLocalScale(sx, sy, 1f);
    }

    private void checkOffScreen(
        final int idx,
        final double y
    ) {
        if ((hiddenMask & (1 << idx)) != 0) {
            return;
        }
        // Fragment is gone once it falls below world Y = 0 (bottom of level)
        // or climbs more than 20 tiles above its spawn tile (sanity cap).
        if (y < 0.0 || y > worldOffset.y() + 20.0) {
            hiddenMask |= (1 << idx);
            rootNode.detachChild(fragmentGeometries[idx]);
        }
    }
}
