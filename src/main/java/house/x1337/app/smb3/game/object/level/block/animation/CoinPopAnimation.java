package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.PopAnimation;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;

@Getter
@Prototype
public final class CoinPopAnimation extends PopAnimation {
    private final Dimensions dimensions = halfTileWidth("CoinPopAnimation");
    private final PopMotion motion;

    @Value("classpath:/sprites/object/coin/popping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    @Setter
    private boolean expired = false;
    private int currentTextureIndex;

    public CoinPopAnimation(
        final GameEngine gameEngine,
        final Reward.Data rewardData,
        final Coordinates offset,
        final PopMotion motion
    ) {
        super(gameEngine, rewardData, offset);
        this.motion = motion;
        this.currentTextureIndex = motion.textureIndexAt(0);
    }

    @Override
    public void onFrameAdvanced(final Geometry spriteGeometry, final int frameIndex) {
        final int newTextureIndex = motion.textureIndexAt(frameIndex);
        if (newTextureIndex != currentTextureIndex) {
            currentTextureIndex = newTextureIndex;
            spriteGeometry.getMaterial().setTexture("ColorMap", animationFrames.getFrame(currentTextureIndex));
        }
    }

    @Override
    public Texture textureForSpriteGeometry() {
        return animationFrames.getFrame(currentTextureIndex);
    }
}
