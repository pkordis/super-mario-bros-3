package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
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
        rebuildWithTexture(node, texture, orientation, quadWidth, quadHeight, rightPadding, 0f);
    }

    /**
     * Builds the player's quad and places it so the body lands over the collision box in either facing.
     *
     * <p>Sprites are authored facing left. Facing right is a negative X scale, which jME swings about the
     * quad's bottom-left origin. The quad is pushed back by its own width, less {@code rightPadding},
     * the empty canvas to the right of the body that the mirror has just moved to the left of it.
     *
     * <p>{@code leadingOffset} is that same idea on the other side, and exists for exactly one pose. The
     * kick frame is the only art in the bundle whose body is <b>not</b> flush to the canvas's left edge:
     * the kicking foot overhangs in the direction of travel, which is the <em>leading</em> side. So there
     * is nothing for {@code rightPadding} to consume, and instead the unmirrored draw has to be pulled
     * left by the overhang. Shifting the unmirrored case is the whole of it — the mirrored case already
     * lands correctly once {@code rightPadding} is zero, because mirroring a right-flush body about the
     * quad width puts it left-flush.
     *
     * @param rightPadding  empty canvas to the right of the body, consumed when mirrored
     * @param leadingOffset empty canvas to the <em>left</em> of the body, consumed when not mirrored
     */
    default void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation,
        final float quadWidth,
        final float quadHeight,
        final float rightPadding,
        final float leadingOffset
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
            geometry.setLocalTranslation(-leadingOffset, 0, 0);
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
        rebuildWithTextureDerivedQuad(node, texture, orientation);
    }

    default void rebuildWithTextureDerivedQuad(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        final float widthPixels = texture.getImage().getWidth();
        final float heightPixels = texture.getImage().getHeight();
        rebuildWithTexture(
            node,
            texture,
            orientation,
            widthPixels * PIXELS_TO_GAME_UNITS,
            heightPixels * PIXELS_TO_GAME_UNITS,
            rightPaddingFor(widthPixels)
        );
    }

    default float rightPaddingFor(final float quadWidthPixels) {
        return (quadWidthPixels - TILE_SPRITE_SIZE) * PIXELS_TO_GAME_UNITS;
    }

    /**
     * Clears cached last-rendered frame bookkeeping so the next {@code update}
     * is forced to rebuild the quad. Called on a mode switch (the node is
     * detached and must be repopulated) and when the growing flicker flips size.
     * The sprite-backed animators override this; the empty animator no-ops.
     */
    default void resetState() {
    }

    void setAssets(final A animatorAssets);
    void update(LevelScenePlayer levelScenePlayer);
}
