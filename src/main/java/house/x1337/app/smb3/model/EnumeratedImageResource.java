package house.x1337.app.smb3.model;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.resource.EnumeratedImageResourceType;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class EnumeratedImageResource<E extends EnumeratedImageResourceType> {
    private final Map<E, ImageResource> imageResources;

    public Texture getTextureFor(final E type) {
        return imageResources.get(type).asTexture();
    }

    public int[] getRgbDataFor(final E type) {
        return imageResources.get(type).getRgbData();
    }
}
