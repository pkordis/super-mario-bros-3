package house.x1337.app.smb3.model.game.player.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.ShrunkAnimator;

public record ShrunkAnimatorAssets (
    Texture stillTexture,
    Texture walkTexture,
    Texture skidTexture,
    Texture runTexture1,
    Texture runTexture2,
    Texture jumpTexture,
    Texture fastJumpTexture
) implements AnimatorAssets {
    public static void loadFor(final ShrunkAnimator a) {
        final ShrunkAnimatorAssets shrunkAnimatorAssets = new ShrunkAnimatorAssets(
            a.loadSprite("still.png"),
            a.loadSprite("walking.png"),
            a.loadSprite("rapid_turn.png"),
            a.loadSprite("running_1.png"),
            a.loadSprite("running_2.png"),
            a.loadSprite("jumping.png"),
            a.loadSprite("flying.png")
        );
        a.setAssets(shrunkAnimatorAssets);
    }
}
