package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.animation.base.BlockBounceAnimation;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;

/**
 * Bounces a plain brick block that a small player hit from below, pausing the brick's shimmer
 * animation for the duration so the animator does not repaint the cell the bounce borrowed.
 */
@Getter
public final class BrickBlockBounceAnimation extends BlockBounceAnimation {
    private static final String SPRITE_ASSET_PATH = "sprites/object/brick/plain/frame_0.png";

    private final GameObjectAnimatorSingleTiled<?> animator;

    public BrickBlockBounceAnimation(
        final GameEngine gameEngine,
        final GameObjectAnimatorSingleTiled<?> animator,
        final Offset offset
    ) {
        this.animator = animator;
        super(
            gameEngine,
            offset
        );
    }

    @Override
    protected Texture generateSpriteTexture(final GameEngine gameEngine) {
        return loadTexture(gameEngine.getAssetManager(), SPRITE_ASSET_PATH);
    }

    @Override
    public void onAnimationStart(final Offset offset) {
        // Pause shimmer animation for this brick during bounce
        animator.pauseAt(offset);
    }

    @Override
    public void onAnimationStop(final Offset offset) {
        animator.resumeAt(offset);
    }
}
