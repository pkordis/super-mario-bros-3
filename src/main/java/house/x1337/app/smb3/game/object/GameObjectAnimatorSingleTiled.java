package house.x1337.app.smb3.game.object;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import org.springframework.core.ResolvableType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public abstract class GameObjectAnimatorSingleTiled<A extends AnimatableLevelObject> implements GameObjectAnimator<A> {
    @SuppressWarnings("unchecked")
    private final Class<A> genericType = (Class<A>) ResolvableType
        .forClass(getClass())
        .as(GameObjectAnimatorSingleTiled.class)
        .getGeneric(0)
        .getRawClass();
    private final List<A> animatableLevelObjects = new ArrayList<>();
    private final Set<Offset> pausedOffsets = new HashSet<>();
    private Geometry interactiveObjectsLayerGeometry;
    private LevelSceneDimensions dimensions;
    private int currentFrame;
    private int tick;

    public abstract AnimationImageResource getAnimationFrames();
    protected abstract int getTicksPerFrame();

    public void writeTile(
        final LevelSceneDimensions dimensions,
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

    public void add(final A object) {
        if (getSupportedTypes().stream().anyMatch(type -> type == object.getClass())) {
            animatableLevelObjects.add(object);
        }
    }

    @Override
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

    @Override
    public void update() {
        if (interactiveObjectsLayerGeometry == null || animatableLevelObjects.isEmpty()) {
            return;
        }
        tick++;
        if (tick % getTicksPerFrame() != 0) {
            return;
        }
        final int nextFrame = (currentFrame + 1) % getAnimationFrames().number();
        paintFrame(nextFrame);
        currentFrame = nextFrame;
    }

    @Override
    public void reset() {
        animatableLevelObjects.clear();
        interactiveObjectsLayerGeometry = null;
    }

    public void unregisterAt(final Offset offset) {
        animatableLevelObjects.removeIf(b -> b.getOffset().equals(offset));
    }

    public void pauseAt(final Offset offset) {
        pausedOffsets.add(offset);
    }

    public void resumeAt(final Offset offset) {
        pausedOffsets.remove(offset);
    }

    public boolean isPausedAt(final Offset offset) {
        return pausedOffsets.contains(offset);
    }


    private void paintFrame(final int frameIdx) {
        final Texture2D texture = (Texture2D) interactiveObjectsLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
        final Image image = texture.getImage();
        final ByteBuffer buffer = image.getData(0);
        final int[] pixels = getAnimationFrames().getFrameRgbData(frameIdx);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        for (final A animatableObjects : animatableLevelObjects) {
            // Skip paused blocks (e.g. during bounce animation)
            if (pausedOffsets.contains(animatableObjects.getOffset())) {
                continue;
            }
            writeTile(
                dimensions,
                buffer,
                pixels,
                animatableObjects.getOffset(),
                imageWidth
            );
        }
        image.setUpdateNeeded();
    }
}
