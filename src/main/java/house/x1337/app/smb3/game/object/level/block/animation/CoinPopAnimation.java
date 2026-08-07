package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.util.GameRenderer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;

/**
 * Animates a single-coin reward with the following sequence (60FPS-based - auto-adjusted for more/fewer FPS)
 * - Frames 0-9: coin emerging and rising (frames 2-5 interpolated from 16 → 38)
 * - Frames 10-13: rising (edge sprite frames 14-17 interpolated)
 * - Frames 14-17: continuing rise
 * - Frames 18-22: near peak (frames 22-25 interpolated)
 * - Frames 23-27: beginning descent
 * - Frames 28-31: descending faster
 * - Frames 32-37: final descent
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class CoinPopAnimation implements GameEngineAware, GameRenderer {
    private static final Dimensions COIN_DIMENSIONS = halfTileWidth("CoinPopping");
    private static final int ANIMATION_FRAMES = 38;
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

    private static final float COIN_Z = 0.06f;

    @Value("classpath:/sprites/object/coin/popping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    private final GameEngine gameEngine;
    private final Offset offset;
    private float worldX;
    private float baseWorldY;

    private Geometry spriteGeometry;
    private boolean expired = false;
    private int frameIndex = 0;
    private int currentFrameIndex = FRAME_SEQUENCE[0];

    @PostConstruct
    void init() {
        // Coin X: center of the block minus half coin width (in world units)
        worldX = offset.x() + COIN_DIMENSIONS.width() - COIN_DIMENSIONS.width() / 2;
        // Base Y: top of the block in world coordinates
        baseWorldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y() + COIN_DIMENSIONS.height();

        // Create sprite geometry with first frame
        spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            animationFrames.getFrame(currentFrameIndex),
            COIN_DIMENSIONS
        );

        positionSprite();
        getGameEngine().getRootNode().attachChild(spriteGeometry);
    }

    public void tick() {
        if (expired) {
            return;
        }

        frameIndex++;

        // Check if animation is complete
        if (frameIndex >= ANIMATION_FRAMES) {
            expired = true;
            return;
        }

        // Update texture if needed
        final int newTextureIndex = FRAME_SEQUENCE[frameIndex];
        if (newTextureIndex != currentFrameIndex) {
            currentFrameIndex = newTextureIndex;
            updateSpriteFrame();
        }

        positionSprite();
    }

    public void detach() {
        getGameEngine()
            .getRootNode()
            .detachChild(spriteGeometry);
    }

    public float getCurrentWorldY() {
        final int yOffset = frameIndex < Y_OFFSETS.length ? Y_OFFSETS[frameIndex] : Y_OFFSETS[Y_OFFSETS.length - 1];
        // Y-offset is measured from block top to coin top (positive = above)
        // Add coin height to get the position of the coin's bottom edge for rendering
        return baseWorldY + (float) yOffset / TILE_SPRITE_SIZE;
    }

    public float getCurrentWorldX() {
        return worldX;
    }

    private void updateSpriteFrame() {
        spriteGeometry
            .getMaterial()
            .setTexture(
                "ColorMap",
                animationFrames.getFrame(currentFrameIndex)
            );
    }

    private void positionSprite() {
        final int yOffset = frameIndex < Y_OFFSETS.length ? Y_OFFSETS[frameIndex] : Y_OFFSETS[Y_OFFSETS.length - 1];
        final float coinTopWorldY = baseWorldY + (float) yOffset / TILE_SPRITE_SIZE;
        final float spriteY = coinTopWorldY - COIN_DIMENSIONS.height();
        spriteGeometry.setLocalTranslation(worldX, spriteY, COIN_Z);
    }
}
