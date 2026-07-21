package house.x1337.app.smb3.model;

import house.x1337.app.smb3.config.ImageResourceConverter;

/**
 * An eagerly-loaded image resource containing raw ARGB pixel data and its
 * dimensions. Intended for use with Spring's {@code @Value} injection so
 * that a classpath PNG can be resolved directly into pixel data at bean
 * creation time, without requiring a manual {@code @PostConstruct} loading
 * step.
 *
 * <p>Usage:
 * <pre>{@code
 * @Value("classpath:/font/hud/hud_base.png")
 * private ImageResource hudBaseImage;
 * }</pre>
 *
 * <p>Spring resolves the classpath expression to a {@code Resource}, then the
 * registered {@link ImageResourceConverter}
 * reads the PNG and populates this object with the decoded ARGB data.
 *
 * @param rgbData Row-major ARGB pixel data (TYPE_INT_ARGB layout).
 * @param width   Image width in pixels.
 * @param height  Image height in pixels.
 * @see ImageResourceConverter
 */
public record ImageResource(
    int[] rgbData,
    int width,
    int height
) {
    public ImageResource copy() {
        final int[] clonedPixels = new int[width * height];
        System.arraycopy(rgbData, 0, clonedPixels, 0, clonedPixels.length);
        return new ImageResource(clonedPixels, width, height);
    }
}
