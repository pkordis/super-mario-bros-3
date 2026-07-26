package house.x1337.app.smb3.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ImageResource {
    private final int[] rgbData;
    private final int width;
    private final int height;

    public ImageResource copy() {
        final int[] clonedPixels = new int[width * height];
        System.arraycopy(rgbData, 0, clonedPixels, 0, clonedPixels.length);
        return new ImageResource(clonedPixels, width, height);
    }
}
