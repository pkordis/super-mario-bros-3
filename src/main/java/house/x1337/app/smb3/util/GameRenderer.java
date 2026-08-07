package house.x1337.app.smb3.util;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.model.game.Dimensions;

import java.nio.ByteBuffer;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static com.jme3.texture.image.ColorSpace.Linear;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SCALE;

// TODO: check if it could extend the GameEngineAware
public interface GameRenderer {
    default Texture2D toTexture(
        final ByteBuffer buffer,
        final int width,
        final int height
    ) {
        buffer.flip();

        final Image image = new Image(RGBA8, width, height, buffer, Linear);
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
        final int width,
        final int height
    ) {
        final ByteBuffer buffer = createByteBuffer(rgbData.length * TILE_SCALE);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = rgbData[y * width + x];
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));         // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        return toTexture(buffer, width, height);
    }
}
