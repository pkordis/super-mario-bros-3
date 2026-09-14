package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.block.animation.base.BlockBounceAnimation;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;

/**
 * Bounces an already emptied block that the player hit from below again. The sprite is the
 * empty-block tile itself, shared through the {@link ImageResource} its manager keeps.
 */
@Getter
public final class EmptyBlockBounceAnimation extends BlockBounceAnimation {
    private final ImageResource imageResource;

    public EmptyBlockBounceAnimation(
        final GameEngine gameEngine,
        final Offset offset,
        final ImageResource imageResource
    ) {
        this.imageResource = imageResource;
        super(
            gameEngine,
            offset
        );
    }

    @Override
    protected Texture generateSpriteTexture(final GameEngine gameEngine) {
        return imageResource.asTexture();
    }
}
