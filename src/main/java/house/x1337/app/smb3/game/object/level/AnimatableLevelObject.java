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

    default void eraseFromBakedTexture(
        final Geometry interactiveObjectsLayerGeometry,
        final LevelSceneDimensions dimensions
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
                buffer.put(indexOffset, (byte) 0);
                buffer.put(indexOffset + 1, (byte) 0);
                buffer.put(indexOffset + 2, (byte) 0);
                buffer.put(indexOffset + 3, (byte) 0);
            }
        }
        image.setUpdateNeeded();
    }
}
