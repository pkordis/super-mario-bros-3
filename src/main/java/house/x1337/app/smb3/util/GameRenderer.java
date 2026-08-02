package house.x1337.app.smb3.util;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.model.game.Dimensions;

import java.nio.ByteBuffer;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static com.jme3.texture.image.ColorSpace.Linear;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;

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
        final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", texture);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.getAdditionalRenderState().setDepthWrite(false);
        mat.getAdditionalRenderState().setDepthTest(false);
        geometry.setMaterial(mat);
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
}
