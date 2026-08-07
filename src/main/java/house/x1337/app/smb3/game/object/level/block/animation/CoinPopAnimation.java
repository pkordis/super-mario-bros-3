package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;

/**
 * Animates a single-coin reward with the following sequence (60FPS-based):
 * <ul>
 *   <li>Frames 0-9: coin emerging and rising</li>
 *   <li>Frames 10-17: continuing rise</li>
 *   <li>Frames 18-22: near peak</li>
 *   <li>Frames 23-31: descent</li>
 *   <li>Frames 32-37: final descent</li>
 * </ul>
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class CoinPopAnimation implements PopAnimation {
    private static final Dimensions COIN_DIMENSIONS = halfTileWidth("CoinPopping");
    private static final int ANIMATION_FRAMES = 38;
    private static final float Z_DEPTH = 0.06f;
    private static final int[] FRAME_SEQUENCE = {
        0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3,
        0, 0, 0, 0,
        1, 1, 1, 1, 2,
        2, 2, 2, 3, 3,
        3, 3, 0, 0,
        0, 0, 1, 1, 1, 1
    };
    private static final int[] Y_OFFSETS = {
        11, 16, 20, 25, 29, 34, 38, 41, 44, 47,
        50, 52, 54, 56,
        57, 58, 59, 60,
        62, 62, 62, 62, 62,
        61, 60, 59, 58, 56,
        54, 52, 49, 46,
        43, 40, 38, 34, 30, 26
    };

    @Value("classpath:/sprites/object/coin/popping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    private final GameEngine gameEngine;
    private final Offset offset;

    @Setter
    private Geometry spriteGeometry;
    @Setter
    private int frameIndex = 0;
    @Setter
    private boolean expired = false;
    private float baseWorldX;
    private float baseWorldY;
    private int currentTextureIndex = FRAME_SEQUENCE[0];

    @PostConstruct
    void init() {
        baseWorldX = offset.x() + COIN_DIMENSIONS.width() - COIN_DIMENSIONS.width() / 2;
        baseWorldY = getLevelScene().getDimensions().rows() - 1 - offset.y() + COIN_DIMENSIONS.height();

        spriteGeometry = createAndAttachSprite(animationFrames.getFrame(currentTextureIndex), COIN_DIMENSIONS);
        positionSprite();
    }

    @Override
    public void onFrameAdvanced() {
        final int newTextureIndex = FRAME_SEQUENCE[frameIndex];
        if (newTextureIndex != currentTextureIndex) {
            currentTextureIndex = newTextureIndex;
            spriteGeometry.getMaterial().setTexture("ColorMap", animationFrames.getFrame(currentTextureIndex));
        }
    }

    @Override
    public Dimensions getDimensions() {
        return COIN_DIMENSIONS;
    }

    @Override
    public int[] getYOffsets() {
        return Y_OFFSETS;
    }

    @Override
    public int getAnimationFrames() {
        return ANIMATION_FRAMES;
    }

    @Override
    public float getZDepth() {
        return Z_DEPTH;
    }

    public float getCurrentWorldX() {
        return baseWorldX;
    }

    public float getCurrentWorldY() {
        return calculateWorldY();
    }
}
