package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;

import java.nio.ByteBuffer;

import static house.x1337.app.smb3.GameConstants.TILE_SCALE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public interface AnimatableLevelObject extends LevelObject {
    Offset getOffset();

    /**
     * Paints one 16x16 tile of ARGB pixels into this object's cell of the baked interactive-objects
     * layer. Used when a tile permanently changes appearance outside of any animator — e.g. a spent
     * block, or a P-Switch repainted as its static pressed sprite.
     *
     * <p>The row mapping is the same Y-flip as
     * {@code GameObjectAnimatorSingleTiled#writeTile}: jme3's {@link ByteBuffer} row 0 is the
     * <em>bottom</em> of the level image, so the top sprite row lands at the highest buffer row.
     *
     * @param interactiveObjectsLayerGeometry the geometry holding the baked layer texture
     * @param dimensions                      the level's tile dimensions
     * @param pixels                          16x16 ARGB pixels, row-major from the top-left
     */
    default void paintToBakedTexture(
        final Geometry interactiveObjectsLayerGeometry,
        final LevelSceneDimensions dimensions,
        final int[] pixels
    ) {
        final Texture2D texture = (Texture2D) interactiveObjectsLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
        final Image image = texture.getImage();
        final ByteBuffer buffer = image.getData(0);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;
        final Offset offset = getOffset();

        for (int spritePixelRow = 0; spritePixelRow < TILE_SPRITE_SIZE; spritePixelRow++) {
            final int imgRow = (dimensions.rows() - 1 - offset.y()) * TILE_SPRITE_SIZE
                + (TILE_SPRITE_SIZE - 1 - spritePixelRow);
            for (int spritePixelCol = 0; spritePixelCol < TILE_SPRITE_SIZE; spritePixelCol++) {
                final int imgCol = offset.x() * TILE_SPRITE_SIZE + spritePixelCol;
                final int indexOffset = (imgRow * imageWidth + imgCol) * TILE_SCALE;
                final int argb = pixels[spritePixelRow * TILE_SPRITE_SIZE + spritePixelCol];
                buffer.put(indexOffset, (byte) ((argb >> 16) & 0xFF)); // R
                buffer.put(indexOffset + 1, (byte) ((argb >> 8) & 0xFF)); // G
                buffer.put(indexOffset + 2, (byte) (argb & 0xFF)); // B
                buffer.put(indexOffset + 3, (byte) ((argb >>> 24) & 0xFF)); // A
            }
        }
        image.setUpdateNeeded();
    }

    /**
     * Clears this object's cell of the baked interactive-objects layer to fully transparent, so the
     * background layer shows through.
     *
     * @param interactiveObjectsLayerGeometry the geometry holding the baked layer texture
     * @param dimensions                      the level's tile dimensions
     */
    default void eraseFromBakedTexture(
        final Geometry interactiveObjectsLayerGeometry,
        final LevelSceneDimensions dimensions
    ) {
        // An all-zero ARGB tile is exactly "transparent black" in every channel.
        paintToBakedTexture(
            interactiveObjectsLayerGeometry,
            dimensions,
            new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE]
        );
    }
}
