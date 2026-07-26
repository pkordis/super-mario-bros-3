package house.x1337.app.smb3.game.object.level.brick.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.brick.BrickBlock;
import house.x1337.app.smb3.game.object.level.brick.BrickBlockWithoutReward;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Replicates the NES CHR bank cycling animation that gives intact brick tiles their
 * characteristic shimmer.
 *
 * <h2>NES source — {@code prg030.asm}</h2>
 * <p>Every 8 game-ticks the PPU's pattern table bank is swapped through the sequence
 * {@code $60, $62, $64, $66} (= {@code chr096}, {@code chr098}, {@code chr100},
 * {@code chr102}). Brick tiles' CHR data differs subtly across these four banks,
 * creating a diagonal highlight sweep across the brick face over a 32-tick cycle.
 *
 * <h2>Implementation</h2>
 * <p>Rather than a GPU shader or atlas scroll (which would require a custom material),
 * this replicates the effect at the same cost as the NES did: one texture-buffer write
 * per 8 ticks, touching only the pixels of registered brick tiles. The baked
 * {@code "Layer-INTERACTIVE_OBJECTS"} {@link Image} buffer is written
 * directly and marked {@code setUpdateNeeded()} so jme3 re-uploads the GPU texture once
 * per cycle switch — exactly 4 re-uploads per second, each touching only the changed
 * pixels.
 */
@Singleton
public final class BrickBlockAnimator implements GameObjectAnimator<BrickBlock> {
    @Getter
    private final List<Class<? extends AnimatableLevelObject>> supportedTypes = List.of(
        BrickBlockWithoutReward.class
    );

    @Value("classpath:/sprites/object/brick/brick_frame_{0,3}.png")
    private AnimationImageResource shineAnimation;

    /**
     * NES: each CHR page lasts 8 ticks (dasm {@code Counter_1 AND #$18 LSR LSR LSR}).
     */
    private static final int TICKS_PER_FRAME = 8;
    private static final int FRAME_COUNT = 4;

    private final List<BrickBlockWithoutReward> bricks = new ArrayList<>();
    private Geometry interactiveObjectsLayerGeometry;
    private LevelSceneDimensions dimensions;
    private int currentFrame;
    private int tick;

    public void add(final BrickBlock brick) {
        if (brick instanceof BrickBlockWithoutReward brickBlockWithoutReward) {
            bricks.add(brickBlockWithoutReward);
        }
    }

    public void registerLevel(
        final Geometry interactiveObjectsLayerGeometry,
        final LevelSceneDimensions dimensions
    ) {
        this.interactiveObjectsLayerGeometry = interactiveObjectsLayerGeometry;
        this.dimensions = dimensions;
        this.tick = 0;
        this.currentFrame = 0;

        // Paint frame 0 immediately so the first render is correct
        if (interactiveObjectsLayerGeometry != null) {
            paintFrame(0);
        }
    }

    public void unregisterAt(final Offset offset) {
        bricks.removeIf(b -> b.getOffset().equals(offset));
    }

    @Override
    public void reset() {
        bricks.clear();
        interactiveObjectsLayerGeometry = null;
    }

    @Override
    public void tick() {
        if (interactiveObjectsLayerGeometry == null || bricks.isEmpty()) {
            return;
        }
        tick++;
        if (tick % TICKS_PER_FRAME != 0) {
            return;
        }
        final int nextFrame = (currentFrame + 1) % FRAME_COUNT;
        paintFrame(nextFrame);
        currentFrame = nextFrame;
    }

    private void paintFrame(final int frameIdx) {
        final Texture2D texture = (Texture2D) interactiveObjectsLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
        final Image image = texture.getImage();
        final ByteBuffer buf = image.getData(0);
        final int[] pixels = shineAnimation.getFrameRgbData(frameIdx);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        for (final BrickBlockWithoutReward brick : bricks) {
            writeTile(buf, pixels, brick.getOffset(), imageWidth);
        }
        image.setUpdateNeeded();
    }

    private void writeTile(
        final ByteBuffer byteBuffer,
        final int[] pixels,
        final Offset offset,
        final int imageWidth
    ) {
        for (int spriteRow = 0; spriteRow < TILE_SPRITE_SIZE; spriteRow++) {
            // jme3 ByteBuffer row 0 = bottom of level image (highest tileRow index).
            // Y-flip: imgRow = (totalRows - 1 - tileRow) * 16 + (15 - sprRow)
            final int imageRow = (dimensions.rows() - 1 - offset.y()) * TILE_SPRITE_SIZE
                + (TILE_SPRITE_SIZE - 1 - spriteRow);
            for (int spriteColumn = 0; spriteColumn < TILE_SPRITE_SIZE; spriteColumn++) {
                final int imageColumn = offset.x() * TILE_SPRITE_SIZE + spriteColumn;
                final int argb = pixels[spriteRow * TILE_SPRITE_SIZE + spriteColumn];
                final int indexOffset = (imageRow * imageWidth + imageColumn) * 4;
                byteBuffer.put(indexOffset, (byte) ((argb >> 16) & 0xFF)); // R
                byteBuffer.put(indexOffset + 1, (byte) ((argb >> 8) & 0xFF)); // G
                byteBuffer.put(indexOffset + 2, (byte) (argb & 0xFF)); // B
                byteBuffer.put(indexOffset + 3, (byte) ((argb >> 24) & 0xFF)); // A
            }
        }
    }
}
