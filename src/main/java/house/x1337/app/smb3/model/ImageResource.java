package house.x1337.app.smb3.model;

import house.x1337.app.smb3.annotation.Prototype;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.function.Supplier;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Prototype
@Setter(PRIVATE)
@ToString(exclude = {"rgbData", "rgbDataLazyLoader"})
@EqualsAndHashCode(exclude = {"rgbData", "rgbDataLazyLoader"})
public class ImageResource {
    private Supplier<int[]> rgbDataLazyLoader = this::getRgbData;
    private int[] rgbData;
    private int width;
    private int height;

    public int[] getRgbData() {
        if (rgbData == null) {
            rgbData = rgbDataLazyLoader.get();
        }
        return rgbData;
    }

    public static ImageResource fromData(
        final int[] rgbData,
        final int width,
        final int height
    ) {
        final ImageResource imageResource = getBean(ImageResource.class);
        imageResource.setRgbData(rgbData);
        imageResource.setWidth(width);
        imageResource.setHeight(height);
        return imageResource;
    }

    public static ImageResource fromLazilyLoadedData(
        final Supplier<int[]> rgbDataLazyLoader,
        final int width,
        final int height
    ) {
        final ImageResource imageResource = getBean(ImageResource.class);
        imageResource.setRgbDataLazyLoader(rgbDataLazyLoader);
        imageResource.setWidth(width);
        imageResource.setHeight(height);
        return imageResource;
    }

    public ImageResource copy() {
        final int[] clonedPixels = new int[width * height];
        System.arraycopy(rgbData, 0, clonedPixels, 0, clonedPixels.length);
        return fromData(clonedPixels, width, height);
    }
}
