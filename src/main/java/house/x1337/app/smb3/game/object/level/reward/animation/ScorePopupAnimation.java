package house.x1337.app.smb3.game.object.level.reward.animation;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.resource.RewardImageResource;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.PopAnimation;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.scoreRise;
import static house.x1337.app.smb3.game.motion.pop.ScoreRisePopMotion.SCORE_COUNTER;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileHeight;
import static java.lang.Math.floor;
import static java.lang.Math.max;

@Getter
@Prototype
public final class ScorePopupAnimation extends PopAnimation {
    public static final int FALLBACK_SCORES_Y = 5;
    public static final int LOWEST_RISING_SCORES_Y = 4;
    public static final int OBJECT_SCORE_ABOVE_OBJECT_PIXELS = 16;
    public static final int SCORES_Y_LIMIT = 192;
    private static final int SCREEN_Y_MASK = 255;

    private final Dimensions dimensions = halfTileHeight("ScorePopupAnimation");
    private PopMotion motion = scoreRise(0, SCORE_COUNTER);
    private int maxRises = SCORE_COUNTER;

    @Value("house.x1337.app.smb3.enumeration.resource.RewardImageResource")
    private EnumeratedImageResource<RewardImageResource> rewardImages;

    public ScorePopupAnimation(
        final GameEngine gameEngine,
        final Reward.Data rewardData,
        final Coordinates offset
    ) {
        super(gameEngine, rewardData, offset);
    }

    public void placeOver(final ActiveLevelObject object) {
        final PixelPosition position = object.getPosition();
        final double spriteTop = position.y() - OBJECT_SCORE_ABOVE_OBJECT_PIXELS;
        placeAt(position.x(), spriteTop, screenLine(spriteTop));
    }

    public void placeAt(
        final double pixelX,
        final double spriteTop,
        final int screenLine
    ) {
        final int scoresY = screenLine & SCREEN_Y_MASK;
        final int shownScoresY = scoresY >= SCORES_Y_LIMIT ? FALLBACK_SCORES_Y : scoresY;
        maxRises = max(0, shownScoresY - LOWEST_RISING_SCORES_Y + 1);
        final double shownTop = spriteTop + shownScoresY - screenLine;
        setWorldOffset(WorldOffset.of(
            (float) (pixelX / TILE_SPRITE_SIZE),
            getGameEngine().getLevelScene().getDimensions().rows() - (float) (shownTop / TILE_SPRITE_SIZE),
            Z_DEPTH_ITEM_REWARD
        ));
    }

    public int screenLine(final double pixelY) {
        final double screenTop = getGameEngine().getCameraState().getActiveObjectRegion(0).top();
        return (int) floor(pixelY - screenTop);
    }

    @Override
    public void start() {
        motion = scoreRise(getGameEngine().getLevelFrameCounter().getTicks(), maxRises);
        super.start();
        tick();
    }

    @Override
    public Texture textureForSpriteGeometry() {
        return rewardImages.getTextureFor(getRewardData().getImageResource());
    }
}
