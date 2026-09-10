package house.x1337.app.smb3.util;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;

import java.nio.ByteBuffer;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static com.jme3.texture.image.ColorSpace.Linear;
import static com.jme3.util.BufferUtils.createByteBuffer;

public interface GameRenderer {
    int RGBA_BYTES_PER_PIXEL = 4;

    default Texture2D toTexture(
        final ByteBuffer buffer,
        final DimensionsPixels dimensions
    ) {
        buffer.flip();

        final Image image = new Image(RGBA8, dimensions.width(), dimensions.height(), buffer, Linear);
        final Texture2D texture = new Texture2D(image);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }

    default Geometry fromTexture(
        final AssetManager assetManager,
        final Texture texture,
        final Dimensions dimensions
    ) {
        final Geometry geometry = new Geometry(
            dimensions.name(),
            dimensions.toQuad()
        );
        final Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setDepthTest(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Translucent);
        return geometry;
    }

    default Texture loadTexture(
        final AssetManager assetManager,
        final String path
    ) {
        final Texture texture = assetManager.loadTexture(path);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }

    default Texture loadTexture(
        final int[] rgbData,
        final DimensionsPixels dimensions
    ) {
        return loadTexture(rgbData, dimensions, false);
    }

    default Texture loadTexture(
        final int[] rgbData,
        final DimensionsPixels dimensions,
        final boolean flipVertically
    ) {
        final ByteBuffer buffer = createByteBuffer(rgbData.length * RGBA_BYTES_PER_PIXEL);
        // jme3 expects the ByteBuffer in bottom-to-top row order, whereas rgbData (e.g. from
        // BufferedImage#getRGB) is top-to-bottom, so emit source rows in reverse to avoid a
        // vertically flipped texture. A mirrored texture is therefore the source order as-is.
        for (int row = 0; row < dimensions.height(); row++) {
            final int y = flipVertically ? row : dimensions.height() - 1 - row;
            for (int x = 0; x < dimensions.width(); x++) {
                final int argb = rgbData[y * dimensions.width() + x];
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));         // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        return toTexture(buffer, dimensions);
    }
}
