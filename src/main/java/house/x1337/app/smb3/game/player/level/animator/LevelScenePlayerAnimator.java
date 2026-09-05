package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.PlayerIdentityAware;
import house.x1337.app.smb3.game.player.PlayerAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.material.RenderState.FaceCullMode.Off;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;

public interface LevelScenePlayerAnimator<A extends PlayerAnimatorAssets>
    extends
        GameEngineAware,
        PlayerAnimator,
        PlayerIdentityAware {
    PlayerMode getPlayerMode();

    @Override
    default String getFramesParentContext() {
        return "sprites/player/%s/level/%s/"
            .formatted(
                getIdentity().getAnimationFramesPath(),
                getPlayerMode().name().toLowerCase()
            );
    }

    default Texture loadSprite(final String filename) {
        final Texture texture = getAssetManager().loadTexture(getFramesParentContext() + filename);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }

    default void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation,
        final float quadWidth,
        final float quadHeight,
        final float rightPadding
    ) {
        node.detachAllChildren();

        final Quad quad = new Quad(quadWidth, quadHeight);
        final Geometry geometry = new Geometry("Player", quad);

        final Material material = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(Alpha);

        geometry.setMaterial(material);
        geometry.setQueueBucket(Translucent);

        if (orientation == LEFT) {
            geometry.setLocalTranslation(0, 0, 0);
            geometry.setLocalScale(1, 1, 1);
        } else {
            material.getAdditionalRenderState().setFaceCullMode(Off);
            geometry.setLocalScale(-1, 1, 1);
            geometry.setLocalTranslation(quadWidth - rightPadding, 0, 0);
        }

        node.attachChild(geometry);
    }

    default void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        node.detachAllChildren();

        final float widthPixels = texture.getImage().getWidth();
        final float heightPixels = texture.getImage().getHeight();
        final float quadWidth = widthPixels * PIXELS_TO_GAME_UNITS;
        final float quadHeight = heightPixels * PIXELS_TO_GAME_UNITS;
        // Empty space to the right of the left-anchored body; keeps the body
        // aligned with the collision box when the sprite is flipped to face right.
        final float rightPadding = (widthPixels - TILE_SPRITE_SIZE) * PIXELS_TO_GAME_UNITS;

        rebuildWithTexture(node, texture, orientation, quadWidth, quadHeight, rightPadding);
    }

    void setAssets(final A animatorAssets);
    void update(LevelScenePlayer levelScenePlayer);

    /**
     * Clears cached last-rendered frame bookkeeping so the next {@code update}
     * is forced to rebuild the quad. Called on a mode switch (the node is
     * detached and must be repopulated) and when the grow flicker flips size.
     * The sprite-backed animators override this; the empty animator no-ops.
     */
    default void resetState() {
    }
}
