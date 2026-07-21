package house.x1337.app.smb3.util;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;

import java.nio.ByteBuffer;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static com.jme3.texture.image.ColorSpace.Linear;

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
}
