package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.animation.base.BlockBounceAnimation;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * Bounces a plain brick block that a small player hit from below, pausing the brick's shimmer
 * animation for the duration so the animator does not repaint the cell the bounce borrowed.
 */
@Getter
@Prototype
public final class BrickBlockBounceAnimation extends BlockBounceAnimation {
    private static final String SPRITE_ASSET_PATH = "sprites/object/brick/plain/frame_0.png";

    private GameObjectAnimatorSingleTiled<?> animator;

    public BrickBlockBounceAnimation(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        super(
            gameEngine,
            offset
        );
    }

    public void configure(final GameObjectAnimatorSingleTiled<?> animator) {
        this.animator = animator;
        // The bounce started in the constructor, before the animator was known, so the shimmer is paused here
        animator.pauseAt(getOffset());
    }

    @Override
    protected Texture generateSpriteTexture(final GameEngine gameEngine) {
        return loadTexture(gameEngine.getAssetManager(), SPRITE_ASSET_PATH);
    }


    @Override
    public void onAnimationStop(final Coordinates offset) {
        animator.resumeAt(offset);
    }
}
