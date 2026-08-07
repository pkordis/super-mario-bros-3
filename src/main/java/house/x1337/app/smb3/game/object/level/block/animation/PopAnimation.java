package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.util.GameRenderer;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Common interface for popup animations (coins, scores, etc.) that rise from blocks.
 *
 * <p>Implementations must provide:
 * <ul>
 *   <li>Y-offset array defining vertical position per frame</li>
 *   <li>Sprite geometry and dimensions</li>
 *   <li>Base world position (X, Y) and Z-depth</li>
 *   <li>Frame state management (index, expired flag)</li>
 * </ul>
 *
 * <p>The interface provides default implementations for {@link #tick()},
 * {@link #detach()}, and sprite positioning.
 */
public interface PopAnimation extends GameEngineAware, GameRenderer {

    // -------------------------------------------------------------------------
    // Abstract accessors — implementations must provide these
    // -------------------------------------------------------------------------

    Geometry getSpriteGeometry();

    Dimensions getDimensions();

    int[] getYOffsets();

    int getAnimationFrames();

    float getBaseWorldX();

    float getBaseWorldY();

    float getZDepth();

    int getFrameIndex();

    void setFrameIndex(int frameIndex);

    boolean isExpired();

    void setExpired(boolean expired);

    // -------------------------------------------------------------------------
    // Default lifecycle methods
    // -------------------------------------------------------------------------

    /**
     * Advances the animation by one game-tick (1/60th second).
     * Subclasses may override to add frame-specific behavior (e.g., texture cycling).
     */
    default void tick() {
        if (isExpired()) {
            return;
        }

        setFrameIndex(getFrameIndex() + 1);

        if (getFrameIndex() >= getAnimationFrames()) {
            setExpired(true);
            return;
        }

        onFrameAdvanced();
        positionSprite();
    }

    /**
     * Hook called after frame advances but before positioning.
     * Override to handle frame-specific updates (e.g., texture changes).
     */
    default void onFrameAdvanced() {
        // Default: no action
    }

    /**
     * Detaches the sprite from the scene graph.
     */
    default void detach() {
        getRootNode().detachChild(getSpriteGeometry());
    }

    // -------------------------------------------------------------------------
    // Positioning
    // -------------------------------------------------------------------------

    /**
     * Positions the sprite based on current frame's Y-offset.
     */
    default void positionSprite() {
        final float worldY = calculateWorldY() - getDimensions().height();
        getSpriteGeometry().setLocalTranslation(getBaseWorldX(), worldY, getZDepth());
    }

    /**
     * Calculates the current world Y position (top edge of sprite).
     *
     * @return Y position in world coordinates
     */
    default float calculateWorldY() {
        final int[] yOffsets = getYOffsets();
        final int frameIndex = getFrameIndex();
        final int yOffset = frameIndex < yOffsets.length
                ? yOffsets[frameIndex]
                : yOffsets[yOffsets.length - 1];
        return getBaseWorldY() + (float) yOffset / TILE_SPRITE_SIZE;
    }

    // -------------------------------------------------------------------------
    // Initialization helpers
    // -------------------------------------------------------------------------

    /**
     * Creates and attaches a sprite geometry from a texture.
     *
     * @param texture    the texture to display
     * @param dimensions the sprite dimensions
     * @return the created geometry, already attached to the root node
     */
    default Geometry createAndAttachSprite(final Texture texture, final Dimensions dimensions) {
        final Geometry geometry = fromTexture(getAssetManager(), texture, dimensions);
        getRootNode().attachChild(geometry);
        return geometry;
    }

    // -------------------------------------------------------------------------
    // Convenience accessors
    // -------------------------------------------------------------------------

    private Node getRootNode() {
        return getGameEngine().getRootNode();
    }
}
