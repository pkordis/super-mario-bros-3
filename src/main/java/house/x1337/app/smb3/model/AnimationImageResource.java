package house.x1337.app.smb3.model;

import com.jme3.texture.Texture;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import static java.util.stream.Collectors.toSet;

@Builder
@RequiredArgsConstructor
public class AnimationImageResource {
    @Singular
    private final List<ImageResource> imageResources;
    @Getter(lazy = true)
    private final int width = validateConsistency(ImageResource::getWidth);
    @Getter(lazy = true)
    private final int height = validateConsistency(ImageResource::getHeight);

    private int validateConsistency(final ToIntFunction<ImageResource> assetExtractor) {
        assert imageResources != null;
        final Set<Integer> sameValues = imageResources
            .stream()
            .mapToInt(assetExtractor)
            .boxed()
            .collect(toSet());
        if (sameValues.size() != 1) {
            throw new IllegalStateException("Not all images resources have the same dimensions");
        }
        return sameValues.iterator().next();
    }

    public Texture getFrame(final int frameIdx) {
        return imageResources.get(frameIdx).asTexture();
    }

    public int[] getFrameRgbData(final int frameIdx) {
        return imageResources.get(frameIdx).getRgbData();
    }

    public int number() {
        return imageResources.size();
    }
}
