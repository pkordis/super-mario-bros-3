package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.animation.base.BlockBounceAnimation;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Bounces a note block: up when it is struck from below or from the side, down and back up when the player lands on
 * it. The note block's own animation is paused for the duration, and the moving block wears a single, still frame.
 */
@Getter
@Prototype
public final class NoteBlockBounceAnimation extends BlockBounceAnimation {
    private static final String SPRITE_ASSET_PATH = "sprites/object/block/note/frame_1.png";

    private GameObjectAnimatorSingleTiled<?> animator;
    private boolean sinking;

    public NoteBlockBounceAnimation(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        super(
            gameEngine,
            offset
        );
    }

    public void configure(
        final GameObjectAnimatorSingleTiled<?> animator,
        final boolean sinking
    ) {
        this.animator = animator;
        this.sinking = sinking;
        // The bounce started in the constructor, before the animator was known, so the animation is paused here
        animator.pauseAt(getOffset());
    }

    /**
     * The vertical velocity, in pixels per frame with positive downwards, that keeps something on top of a sinking
     * block moving with it on the block's next tick.
     */
    public double getRiderYVelocity() {
        return getCurrentVelocity() / (double) TILE_SPRITE_SIZE;
    }

    @Override
    public boolean isSinking() {
        return sinking;
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
