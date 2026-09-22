package house.x1337.app.smb3.game.object.level.effect;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.ForemostSpriteOverlay;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.Animation;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Getter
@Prototype
public final class TailAttackFlashAnimation implements Animation {
    public static final int LIFETIME_TICKS = 10;
    public static final int FRAME_COUNT = 4;
    private static final int FLIP_PERIOD_TICKS = 4;
    private static final Dimensions WHAM_DIMENSIONS = new Dimensions(
        "Wham",
        TILE_SIZE_GAME_UNITS,
        TILE_SIZE_GAME_UNITS
    );
    private static final DimensionsPixels WHAM_PIXELS = new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE);

    private final ForemostSpriteOverlay overlay;
    private final Geometry spriteGeometry;
    private final Material material;
    private final Texture[][] framesByFlip;
    private final WorldOffset worldOffset;

    private int elapsedTicks;
    private int lastFrameIndex = -1;
    private boolean lastFlipped;
    private boolean expired;

    public TailAttackFlashAnimation(
        final GameEngine gameEngine,
        final Offset offset,
        final AnimationImageResource animationFrames
    ) {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        // Not rootNode: the flash goes into the overlay rendered after the whole game scene, because the
        // ROM gives it Sprite_RAM slot 0 and no depth in the main scene's sort can guarantee that.
        this.overlay = getBean(ForemostSpriteOverlay.class);
        this.framesByFlip = buildTextures(animationFrames);
        this.worldOffset = WorldOffset.of(
            offset.x() / (float) TILE_SPRITE_SIZE,
            (rows - 1) - offset.y() / (float) TILE_SPRITE_SIZE,
            // Depth is not what puts this on top - the overlay's render order does - and the flash is the
            // only thing in there, so it has nothing to sort against.
            0f
        );
        this.spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            framesByFlip[0][0],
            WHAM_DIMENSIONS
        );
        this.worldOffset.applyTo(spriteGeometry);
        this.material = spriteGeometry.getMaterial();

        applyCurrentFrame();
        overlay.attach(gameEngine, spriteGeometry);
    }

    public void tick() {
        if (expired) {
            return;
        }
        elapsedTicks++;
        if (elapsedTicks >= LIFETIME_TICKS) {
            expired = true;
            return;
        }
        applyCurrentFrame();
    }

    public void detach() {
        overlay.detach(spriteGeometry);
    }

    public int frameIndex() {
        return frameIndexAt(elapsedTicks);
    }

    public boolean isFlippedVertically() {
        return isFlippedVerticallyAt(elapsedTicks);
    }

    public int frameIndexAt(final int tick) {
        return tick & (FRAME_COUNT - 1);
    }

    public boolean isFlippedVerticallyAt(final int tick) {
        return (tick & FLIP_PERIOD_TICKS) != 0;
    }

    private void applyCurrentFrame() {
        final int frameIndex = frameIndex();
        final boolean flipped = isFlippedVertically();
        if (frameIndex == lastFrameIndex && flipped == lastFlipped) {
            return;
        }
        lastFrameIndex = frameIndex;
        lastFlipped = flipped;
        material.setTexture("ColorMap", framesByFlip[frameIndex][flipped ? 1 : 0]);
    }

    private Texture[][] buildTextures(final AnimationImageResource animationFrames) {
        final Texture[][] textures = new Texture[FRAME_COUNT][2];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            final int[] pixels = animationFrames.getFrameRgbData(frame);
            textures[frame][0] = loadTexture(pixels, WHAM_PIXELS);
            textures[frame][1] = loadTexture(pixels, WHAM_PIXELS, true);
        }
        return textures;
    }
}
