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
 * Animates a coin popping out of a ? block or brick.
 *
 * <h2>Animation data — extracted from NES capture {@code captures/single_coin_reward_q_block_bounce.png}</h2>
 *
 * <p>The animation was captured at 60fps from an actual NES running SMB3.
 * The coin pops up in a parabolic arc over 38 frames, with the sprite
 * cycling through 4 texture frames in a specific pattern.
 *
 * <h3>Sprite sequence (38 frames)</h3>
 * <pre>
 * 0 0 3 3 3 3 1 1 1 1 2 2 2 2 0 0 0 0 3 3 3 3 2 2 2 2 1 1 1 1 0 0 0 0 3 3 3 3
 * </pre>
 *
 * <h3>Frame mapping</h3>
 * <ul>
 *   <li>0 = frame_0.png (front view, full coin)</li>
 *   <li>1 = frame_1.png (edge view, thin)</li>
 *   <li>2 = frame_2.png (angled view)</li>
 *   <li>3 = frame_3.png (angled view, H-flipped)</li>
 * </ul>
 *
 * <h3>Y-offset trajectory</h3>
 * <p>The coin rises to a peak of 62 NES pixels above the block top,
 * then descends back down. Y-offsets are measured from the TOP edge
 * of the 16px tall coin sprite to the block's top edge.
 */
@Getter
public final class CoinPopAnimation implements GameRenderer {

    /**
     * Sprite frame sequence for 38-frame animation.
     * Each value is a texture index (0-3) indicating which coin sprite to display.
     * Extracted from NES capture: the coin rotates through front→angled→edge→angled pattern.
     */
    private static final int[] FRAME_SEQUENCE = {
        0, 0, 3, 3, 3, 3, 1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 3, 3,
        3, 3, 2, 2, 2, 2, 1, 1, 1, 1, 0, 0, 0, 0, 3, 3, 3, 3
    };

    /**
     * Y-offset for each frame in NES pixels, measured from the TOP of the coin
     * sprite to the block's top edge. Positive values = coin is above the block.
     *
     * <p>Extracted from NES capture {@code single_coin_reward_q_block_bounce.png}. The coin:
     * <ul>
     *   <li>Starts partially inside the block (frames 0-5 overlap with block bounce)</li>
     *   <li>Rises to peak of 62px above block at frame 18-22</li>
     *   <li>Descends back down, ending around 26px above block</li>
     * </ul>
     *
     * <p>Note: Frames 2-5 were originally misdetected as the bouncing block
     * (values 2, 5, 7, 8). These have been interpolated from 16→38 for a smooth arc.
     */
    private static final int[] Y_OFFSETS = {
        // Frames 0-9: coin emerging and rising (frames 2-5 interpolated from 16→38)
        11, 16, 20, 25, 29, 34, 38, 41, 44, 47,
        // Frames 10-13: rising (edge sprite frames 14-17 interpolated)
        50, 52, 54, 56,
        // Frames 14-17: continuing rise
        57, 58, 59, 60,
        // Frames 18-22: near peak (frames 22-25 interpolated)
        62, 62, 62, 62, 62,
        // Frames 23-27: beginning descent
        61, 60, 59, 58, 56,
        // Frames 28-31: descending faster
        54, 52, 49, 46,
        // Frames 32-37: final descent
        43, 40, 38, 34, 30, 26
    };

    /**
     * Total duration of the coin animation in frames.
     */
    private static final int ANIMATION_FRAMES = FRAME_SEQUENCE.length;

    /**
     * Coin sprite dimensions: 8×16 NES pixels.
     */
    private static final Dimensions COIN_DIMENSIONS = new Dimensions(
        "CoinPop",
        8.0f / TILE_SPRITE_SIZE,
        16.0f / TILE_SPRITE_SIZE
    );

    /**
     * Z-depth for the coin — in front of blocks but behind the score popup.
     */
    private static final float COIN_Z = 0.06f;

    /**
     * Asset paths for the 4 coin frame textures.
     * <ul>
     *   <li>frame_0.png: front view (full coin visible)</li>
     *   <li>frame_1.png: edge view (thin line)</li>
     *   <li>frame_2.png: angled view</li>
     *   <li>frame_3.png: angled view, horizontally flipped (pre-baked)</li>
     * </ul>
     */
    private static final String[] COIN_FRAME_ASSETS = {
        "sprites/object/coin/frame_0.png",
        "sprites/object/coin/frame_1.png",
        "sprites/object/coin/frame_2.png",
        "sprites/object/coin/frame_3.png",
    };

    // -- Position fields --

    private final Offset offset;
    private final float worldX;
    private final float baseWorldY;

    // -- State --

    private final Node rootNode;
    private final Texture[] coinTextures;
    private final Geometry spriteGeometry;
    private int frameIndex;
    private boolean expired;
    private int currentTextureIndex;

    // ----------------------------------------------------------------------

    /**
     * Creates a new coin pop animation.
     *
     * @param gameEngine the game engine
     * @param offset     the tile offset of the block that was hit
     */
    public CoinPopAnimation(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        this.offset = offset;
        // Coin X: center of the block minus half coin width
        this.worldX = offset.x() + 0.5f - COIN_DIMENSIONS.width() / 2;
        // Base Y: top of the block in world coordinates
        this.baseWorldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y() + 1.0f;
        this.rootNode = gameEngine.getRootNode();

        this.frameIndex = 0;
        this.expired = false;
        this.currentTextureIndex = FRAME_SEQUENCE[0];

        // Load all coin frame textures
        this.coinTextures = new Texture[COIN_FRAME_ASSETS.length];
        for (int i = 0; i < COIN_FRAME_ASSETS.length; i++) {
            coinTextures[i] = loadTexture(gameEngine.getAssetManager(), COIN_FRAME_ASSETS[i]);
        }

        // Create sprite geometry with first frame
        this.spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            coinTextures[currentTextureIndex],
            COIN_DIMENSIONS
        );

        positionSprite();
        rootNode.attachChild(spriteGeometry);
    }

    /**
     * Advances the coin animation by one game-tick (1/60th second).
     *
     * <p>Uses lookup tables extracted from NES capture for pixel-perfect
     * reproduction of the original animation timing and trajectory.
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

        // Update texture if needed
        final int newTextureIndex = FRAME_SEQUENCE[frameIndex];
        if (newTextureIndex != currentTextureIndex) {
            currentTextureIndex = newTextureIndex;
            updateSpriteFrame();
        }

        positionSprite();
    }

    /**
     * Returns true when the coin has completed its arc.
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * Detaches the coin sprite from the scene graph.
     */
    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    /**
     * Returns the current world Y position of the coin's top edge.
     * Used by the score popup to spawn at the coin's final position.
     *
     * @return current Y position in world coordinates
     */
    public float getCurrentWorldY() {
        final int yOffset = frameIndex < Y_OFFSETS.length ? Y_OFFSETS[frameIndex] : Y_OFFSETS[Y_OFFSETS.length - 1];
        // Y-offset is measured from block top to coin top (positive = above)
        // Add coin height to get the position of the coin's bottom edge for rendering
        return baseWorldY + (float) yOffset / TILE_SPRITE_SIZE;
    }

    /**
     * Returns the current world X position of the coin.
     *
     * @return current X position in world coordinates
     */
    public float getCurrentWorldX() {
        return worldX;
    }

    private void updateSpriteFrame() {
        spriteGeometry.getMaterial().setTexture("ColorMap", coinTextures[currentTextureIndex]);
    }

    private void positionSprite() {
        // Get Y-offset for current frame (in NES pixels, positive = above block)
        final int yOffset = frameIndex < Y_OFFSETS.length ? Y_OFFSETS[frameIndex] : Y_OFFSETS[Y_OFFSETS.length - 1];

        // Convert to world coordinates
        // Y-offset is from block top to coin top, so we add it to baseWorldY
        // But we need to position the sprite's bottom edge, so add coin height
        final float coinTopWorldY = baseWorldY + (float) yOffset / TILE_SPRITE_SIZE;
        // Sprite is positioned by its bottom-left corner in jme3, so subtract height
        final float spriteY = coinTopWorldY - COIN_DIMENSIONS.height();

        spriteGeometry.setLocalTranslation(worldX, spriteY, COIN_Z);
    }
}
