package house.x1337.app.smb3.game.player.level;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.PlayerRuntimeStateAware;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerIdentityAware;
import house.x1337.app.smb3.game.player.PlayerModeAware;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.game.player.level.animator.RaccoonAnimator;
import house.x1337.app.smb3.model.game.player.PlayerPosition;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.BACKGROUND;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.FOREGROUND_LAYERS;

public interface LevelScenePlayerRenderer
    extends
        GameEngineAware,
        PlayerRuntimeStateAware,
        PlayerIdentityAware,
        PlayerModeAware,
        Player {

    /**
     * Returns the box height in game-units (tile-fractions) for the current
     * effective size. The height is derived from the collision probe extents:
     * Large/standing: head at Y+6, feet at Y+32 → 26px visible, but full
     * sprite is 32px. Small/ducking: head at Y+16, feet at Y+32 → 16px = 1.0.
     */
    default float currentBoxHeight() {
        final boolean compact = isSmall() || getRuntimeState().isDucking();
        return (float) (compact ? TILE_SPRITE_SIZE : TILE_SPRITE_SIZE * 2) / TILE_SPRITE_SIZE;
    }

    default Node createNode() {
        // Assign the node field BEFORE building geometry. rebuildGeometry()
        // drives the active animator, which reads getNode() (the field) rather
        // than any passed-in reference — building before the assignment would
        // hand the animator a null node.
        setNode(new Node("PlayerNode"));
        rebuildGeometry(getNode());
        return getNode();
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
            final LevelScenePlayerAnimationContext animationContext = getAnimationContext();
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

    default void updateForegroundLayerBuckets() {
        final Node rootNode = getGameEngine().getRootNode();
        final boolean background = (getVisibility() == BACKGROUND);

        for (final String layerName : FOREGROUND_LAYERS) {
            final Spatial layerSpatial = rootNode.getChild(layerName);
            if (layerSpatial instanceof Geometry layerGeometry) {
                if (background) {
                    // Move to Translucent and re-attach after player so it renders on top
                    layerGeometry.setQueueBucket(Translucent);
                    rootNode.detachChild(layerGeometry);
                    rootNode.attachChild(layerGeometry);
                } else {
                    // Restore to Transparent (renders before player's Translucent)
                    layerGeometry.setQueueBucket(Transparent);
                }
            }
        }
    }

    @Override
    default void renderPlayer() {
        // Assets (and each animator's sprite spec) must be loaded before the
        // first geometry build, because createNode() -> rebuildGeometry() runs
        // the active animator's update(), which reads the loaded textures.
        getAnimationContext().loadAssets();
        createNode();
        getGameEngine()
            .getRootNode()
            .attachChild(getNode());
        updateVisualPosition();
    }

    @Override
    default void updateVisualPosition() {
        if (getNode() == null || getLevelScene() == null) {
            return;
        }
        final PlayerPosition position = getPosition();
        final LevelScene levelScene = getGameEngine().getLevelScene();

        final PlayerPosition tileUnitBasedPosition = position.toTileUnitBased(levelScene.getDimensions());
        getNode()
            .setLocalTranslation(
                (float) tileUnitBasedPosition.getX(),
                (float) tileUnitBasedPosition.getY() - 2,
                getVisibility().getPlayerZ()
            );
    }

    Node getNode();
    void setNode(Node node);
    void advanceAnimation();
    LevelScenePlayerAnimationContext getAnimationContext();
    PlayerVisibility getVisibility();
}

