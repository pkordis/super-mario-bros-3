package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.game.object.level.PopAnimation;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.game.motion.pop.PopMotions.parabolic;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;
import static house.x1337.app.smb3.model.game.WorldOffset.of;

@Getter
@Prototype
public final class CoinPopAnimation extends PopAnimation {
    private final Dimensions dimensions = halfTileWidth("CoinPopAnimation");
    private final PopMotion motion = parabolic(11, 647, 32, 128, 38).spinning(4, 4, 2);

    @Value("classpath:/sprites/object/coin/popping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    @Setter
    private boolean expired = false;
    private int currentTextureIndex = motion.textureIndexAt(0);

    public CoinPopAnimation(
        final GameEngine gameEngine,
        final Reward.Data rewardData,
        final Offset offset
    ) {
        super(gameEngine, rewardData, offset);
    }

    @Override
    public WorldOffset adjustWorldOffset(
        final WorldOffset worldOffset,
        final Offset offset
    ) {
        return of(
            offset.x() + dimensions.width() - dimensions.width() / 2,
            getLevelScene().getDimensions().rows() - 1 - offset.y() + dimensions.height(),
            Z_DEPTH_ITEM_REWARD
        );
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
