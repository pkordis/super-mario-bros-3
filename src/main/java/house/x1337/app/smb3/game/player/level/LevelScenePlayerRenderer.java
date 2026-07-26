package house.x1337.app.smb3.game.player.level;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerOrientationVertical;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.ActivePlayerStateAware;
import house.x1337.app.smb3.game.player.PlayerIdentityAware;
import house.x1337.app.smb3.game.player.PlayerModeAware;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.game.player.level.animator.RaccoonAnimator;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;

public interface LevelScenePlayerRenderer
    extends
        ActivePlayerStateAware,
        GameEngineAware,
        PlayerIdentityAware,
        PlayerModeAware {

    /**
     * Returns the box height in game-units (tile-fractions) for the current
     * effective size. The height is derived from the collision probe extents:
     * Large/standing: head at Y+6, feet at Y+32 → 26px visible, but full
     * sprite is 32px. Small/ducking: head at Y+16, feet at Y+32 → 16px = 1.0.
     */
    default float currentBoxHeight() {
        final boolean compact = isSmall() || getState().isDucking();
        return (compact ? 16.0f : 32.0f) / 16.0f;
    }

    default Node createNode() {
        final Node node = new Node("PlayerNode");
        rebuildGeometry(node);
        return node;
    }

    /**
     * Rebuilds the player quad geometry to match the current size, replacing
     * any existing geometry attached to the node. For raccoon mode, the
     * initial geometry uses the still sprite (wider, 24px) — subsequent
     * updates are driven by {@link RaccoonAnimator}.
     */
    default void rebuildGeometry(final Node node) {
        node.detachAllChildren();

        if (this instanceof LevelScenePlayer levelScenePlayer) {
            final LevelScenePlayerAnimationContext animationContext = getPlayerAnimationContext();
            animationContext.updateActiveAnimator(levelScenePlayer);
            animationContext.update(levelScenePlayer);
        }
    }

    /**
     * Builds the legacy cyan colored box geometry at the current player size.
     * Used as a fallback for modes/states without sprite-based rendering.
     */
    default void buildCyanBox(final Node node) {
        node.detachAllChildren();

        // Player box size in game-units (tile-units): 1 tile = 1.0 game-units.
        // Large: 12px wide × 32px tall → 0.75 × 2.0 game-units
        // Small: 12px wide × 16px tall → 0.75 × 1.0 game-units
        final float boxWidth = 12.0f / 16.0f;
        final float boxHeight = currentBoxHeight();

        final Quad quad = new Quad(boxWidth, boxHeight);
        final Geometry geometry = new Geometry("Player", quad);
        final Material material = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0, 1, 1, 1)); // Cyan like the JS reference
        material.getAdditionalRenderState().setBlendMode(Alpha);

        geometry.setMaterial(material);
        geometry.setQueueBucket(Transparent);

        node.attachChild(geometry);
    }

    void advanceAnimation();
    LevelScenePlayerAnimationContext getPlayerAnimationContext();
    PlayerOrientationHorizontal getPlayerOrientationHorizontal();
    PlayerOrientationVertical getPlayerOrientationVertical();
}

