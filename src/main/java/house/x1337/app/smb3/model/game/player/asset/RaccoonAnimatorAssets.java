package house.x1337.app.smb3.model.game.player.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.RaccoonAnimator;

public record RaccoonAnimatorAssets(
    Texture stillTexture,
    Texture walkTexture1,
    Texture walkTexture2,
    Texture runTexture1,
    Texture runTexture2,
    Texture runTexture3,
    Texture skidTexture,
    Texture duckTexture,
    Texture jumpTexture,
    Texture tailFallTexture1,
    Texture tailFallTexture2,
    Texture tailFlyTexture1,
    Texture tailFlyTexture2,
    Texture tailFlyTexture3,
    Texture tailAttackTexture1,
    Texture tailAttackTexture2,
    Texture tailAttackTexture3
) implements AnimatorAssets {
    public static void loadFor(final RaccoonAnimator a) {
        final RaccoonAnimatorAssets raccoonAnimatorAssets = new RaccoonAnimatorAssets(
            a.loadSprite("still.png"),
            a.loadSprite("walking_1.png"),
            a.loadSprite("walking_2.png"),
            a.loadSprite("running_1.png"),
            a.loadSprite("running_2.png"),
            a.loadSprite("running_3.png"),
            a.loadSprite("rapid_turn.png"),
            a.loadSprite("ducking.png"),
            a.loadSprite("jumping.png"),
            a.loadSprite("tail_wagging_control_fall_1.png"),
            a.loadSprite("tail_wagging_control_fall_2.png"),
            a.loadSprite("tail_wagging_control_fly_1.png"),
            a.loadSprite("tail_wagging_control_fly_2.png"),
            a.loadSprite("tail_wagging_control_fly_3.png"),
            a.loadSprite("tail_wagging_attack_1.png"),
            a.loadSprite("tail_wagging_attack_2.png"),
            a.loadSprite("tail_wagging_attack_3.png")
        );
        a.setAssets(raccoonAnimatorAssets);
    }
}
