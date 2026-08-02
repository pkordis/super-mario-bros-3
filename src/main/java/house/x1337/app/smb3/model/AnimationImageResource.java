package house.x1337.app.smb3.model;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

import java.util.List;

@Builder
@RequiredArgsConstructor
public class AnimationImageResource {
    @Singular
    private final List<ImageResource> imageResources;

    public int[] getFrameRgbData(final int frameIdx) {
        return imageResources.get(frameIdx).getRgbData();
    }

    public int number() {
        return imageResources.size();
    }
}
