package house.x1337.app.smb3.game.object.level.reward.animation;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.resource.RewardImageResource;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.PopAnimation;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.deceleratingRise;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileHeight;

@Getter
@Prototype
public final class ScorePopupAnimation extends PopAnimation {
    private static final float SCORE_CAPTION_HEIGHT = TILE_SIZE_GAME_UNITS / 2f;
    private static final float SCORE_CAPTION_INITIAL_DIP = 4f / TILE_SPRITE_SIZE;
    private static final float SCORE_CAPTION_ABOVE_INSTANCE_LIFT = SCORE_CAPTION_HEIGHT + SCORE_CAPTION_INITIAL_DIP;
    private final Dimensions dimensions = halfTileHeight("ScorePopupAnimation");
    private final PopMotion motion = deceleratingRise(16, 3);

    @Value("house.x1337.app.smb3.enumeration.resource.RewardImageResource")
    private EnumeratedImageResource<RewardImageResource> rewardImages;

    public ScorePopupAnimation(
        final GameEngine gameEngine,
        final Reward.Data rewardData,
        final Offset offset
    ) {
        super(gameEngine, rewardData, offset);
    }

    @Override
    public Texture textureForSpriteGeometry() {
        return rewardImages.getTextureFor(getRewardData().getImageResource());
    }
}
