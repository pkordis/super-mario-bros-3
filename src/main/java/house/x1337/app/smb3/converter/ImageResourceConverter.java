package house.x1337.app.smb3.converter;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.converter.base.BaseValueConverter;
import house.x1337.app.smb3.model.ImageResource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import static house.x1337.app.smb3.model.ImageResource.fromData;

@Slf4j
@Singleton
public class ImageResourceConverter extends BaseValueConverter<ImageResource> {
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @NonNull
    @Override
    public ImageResource convert(@NonNull final String source) {
        final Resource resource = resourceLoader.getResource(source);
        try (final InputStream is = resource.getInputStream()) {
            final BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new IOException("Failed to read image resource: " + source);
            }
            final int width = image.getWidth();
            final int height = image.getHeight();
            final int[] rgbData = new int[width * height];
            image.getRGB(0, 0, width, height, rgbData, 0, width);
            return fromData(rgbData, width, height);
        } catch (final IOException e) {
            log.error("Failed to load image resource: {}", source, e);
            return fromData(new int[0], 0, 0);
        }
    }
}
