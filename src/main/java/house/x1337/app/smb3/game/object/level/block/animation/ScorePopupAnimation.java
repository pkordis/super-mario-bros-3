package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileHeight;

/**
 * Animates the "100" score popup that appears when a coin expires with the following sequence (60FPS-based):
 *
 * <ul>
 *   <li>Frames 0-16: rises 1 pixel per frame (fast)</li>
 *   <li>Frames 17-32: rises 1 pixel per 2 frames (medium)</li>
 *   <li>Frames 33-48: rises 1 pixel per 4 frames (slow)</li>
 * </ul>
 */
@Getter
@Prototype
public final class ScorePopupAnimation implements PopAnimation {
    private static final Dimensions SPRITE_DIMENSIONS = halfTileHeight("Score100");
    private static final int ANIMATION_FRAMES = 48;
    private static final float Z_DEPTH = 0.06f;
    private static final String SCORE_ASSET = "sprites/object/score/score_100.png";

    /** X offset: -4 NES pixels (centers 16px score over 8px coin). */
    private static final float X_OFFSET = -4.0f / TILE_SPRITE_SIZE;

    /** Sprite dimensions: 16×8 NES pixels. */

    private static final int[] Y_OFFSETS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        16, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 23, 23,
        24, 24, 24, 24, 25, 25, 25, 25, 26, 26, 26, 26, 27, 27, 27, 27
    };

    @Value("classpath:/sprites/object/coin/popping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    private final GameEngine gameEngine;
    private final Offset offset;
    private final float baseWorldX;
    private final float baseWorldY;

    @Setter
    private Geometry spriteGeometry;
    @Setter
    private int frameIndex = 0;
    @Setter
    private boolean expired = false;

    public ScorePopupAnimation(
        final GameEngine gameEngine,
        final Offset offset,
        final float coinWorldX,
        final float coinWorldY
    ) {
        this.gameEngine = gameEngine;
        this.offset = offset;
        this.baseWorldX = coinWorldX + X_OFFSET;
        this.baseWorldY = coinWorldY;

        this.spriteGeometry = createAndAttachSprite(
            loadTexture(getAssetManager(), SCORE_ASSET),
            SPRITE_DIMENSIONS
        );
        positionSprite();
    }

    @Override
    public Dimensions getDimensions() {
        return SPRITE_DIMENSIONS;
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
}
