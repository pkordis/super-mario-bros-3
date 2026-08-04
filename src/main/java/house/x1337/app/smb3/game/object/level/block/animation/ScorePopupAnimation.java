package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Animates the "100" score popup that appears when a coin expires.
 *
 * <h2>Animation data — extracted from NES capture {@code captures/single_coin_reward_q_block_bounce.png}</h2>
 *
 * <p>The score popup appears 1 frame after the coin animation ends and
 * rises upward over 48 frames with a decelerating pattern:
 * <ul>
 *   <li>Frames 0-16: rises 1 pixel per frame (fast)</li>
 *   <li>Frames 17-32: rises 1 pixel per 2 frames (medium)</li>
 *   <li>Frames 33-48: rises 1 pixel per 4 frames (slow)</li>
 * </ul>
 *
 * <h3>Capture details</h3>
 * <ul>
 *   <li>Score appears at frame 43 in the strip (1 frame after coin ends at 41)</li>
 *   <li>Score disappears after frame 90 (48-frame duration)</li>
 *   <li>Initial Y: 96 NES pixels from top of screen</li>
 *   <li>Final Y: 69 NES pixels (rises 27 pixels total)</li>
 * </ul>
 */
@Getter
public final class ScorePopupAnimation implements GameRenderer {

    /**
     * Rise offset for each frame in NES pixels.
     * The score starts at a base position and rises by these cumulative amounts.
     *
     * <p>Pattern extracted from NES capture:
     * <ul>
     *   <li>Frames 0-16: +1 per frame (total 0→16)</li>
     *   <li>Frames 17-32: +1 per 2 frames (total 16→24)</li>
     *   <li>Frames 33-47: +1 per 4 frames (total 24→27)</li>
     * </ul>
     */
    private static final int[] RISE_OFFSETS = {
        // Frames 0-16: fast rise (1px/frame)
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        // Frames 17-32: medium rise (1px/2frames)
        16, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 23, 23,
        // Frames 33-47: slow rise (1px/4frames)
        24, 24, 24, 24, 25, 25, 25, 25, 26, 26, 26, 26, 27, 27, 27, 27
    };

    /**
     * Total duration of the score popup animation in frames.
     */
    private static final int ANIMATION_FRAMES = RISE_OFFSETS.length;

    /**
     * X offset from coin position: -4 NES pixels (centers the 16px-wide score
     * over the 8px-wide coin position).
     */
    private static final float X_OFFSET = -4.0f / TILE_SPRITE_SIZE;

    /**
     * Sprite dimensions: 16×8 NES pixels ("1" + "00" glyphs combined).
     */
    private static final Dimensions SPRITE_DIMENSIONS = new Dimensions(
        "Score100",
        16.0f / TILE_SPRITE_SIZE,
        8.0f / TILE_SPRITE_SIZE
    );

    /**
     * Z-depth for the score — in front of the coin.
     */
    private static final float SCORE_Z = 0.07f;

    /**
     * Asset path for the combined "100" sprite.
     */
    private static final String SCORE_ASSET = "sprites/object/score/score_100.png";

    // -- Position fields --

    private final Offset offset;
    private final float baseWorldX;
    private final float baseWorldY;

    // -- State --

    private final Node rootNode;
    private final Geometry spriteGeometry;
    private int frameIndex;
    private boolean expired;

    // ----------------------------------------------------------------------

    /**
     * Creates a new score popup animation at the coin's final position.
     *
     * @param gameEngine  the game engine
     * @param offset      the tile offset of the block that was hit
     * @param coinWorldX  the world X position of the expired coin
     * @param coinWorldY  the world Y position of the expired coin (top edge)
     */
    public ScorePopupAnimation(
            final GameEngine gameEngine,
            final Offset offset,
            final float coinWorldX,
            final float coinWorldY) {
        this.offset = offset;
        // Center the score above the coin position
        this.baseWorldX = coinWorldX + X_OFFSET;
        // Score spawns at coin's Y position
        this.baseWorldY = coinWorldY;
        this.rootNode = gameEngine.getRootNode();

        this.frameIndex = 0;
        this.expired = false;

        final Texture texture = loadTexture(gameEngine.getAssetManager(), SCORE_ASSET);
        this.spriteGeometry = fromTexture(gameEngine.getAssetManager(), texture, SPRITE_DIMENSIONS);

        positionSprite();
        rootNode.attachChild(spriteGeometry);
    }

    /**
     * Advances the score popup animation by one game-tick (1/60th second).
     *
     * <p>Uses lookup table extracted from NES capture for pixel-perfect
     * reproduction of the original rising animation.
     */
    public void tick() {
        if (expired) {
            return;
        }

        frameIndex++;

        // Check if animation is complete
        if (frameIndex >= ANIMATION_FRAMES) {
            expired = true;
            return;
        }

        positionSprite();
    }

    /**
     * Returns true when the score popup has finished.
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * Detaches the score sprite from the scene graph.
     */
    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    private void positionSprite() {
        // Get rise offset for current frame
        final int riseOffset = frameIndex < RISE_OFFSETS.length
            ? RISE_OFFSETS[frameIndex]
            : RISE_OFFSETS[RISE_OFFSETS.length - 1];

        // Calculate world Y: base position plus rise amount
        // Rise offset is in NES pixels, positive = upward
        final float worldY = baseWorldY + (float) riseOffset / TILE_SPRITE_SIZE;

        spriteGeometry.setLocalTranslation(baseWorldX, worldY, SCORE_Z);
    }
}
