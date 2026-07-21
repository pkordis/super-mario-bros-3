package house.x1337.app.smb3.config;

import house.x1337.app.smb3.model.ImageResource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * Spring {@link Converter} that transforms a classpath location string (e.g.
 * {@code "classpath:/font/hud/hud_base.png"}) into an {@link ImageResource}
 * by resolving the resource and extracting its ARGB pixel data and dimensions.
 *
 * <p>This enables idiomatic injection of fully-decoded image resources:
 * <pre>{@code
 * @Value("classpath:/font/hud/hud_base.png")
 * private ImageResource hudBaseImage;
 * }</pre>
 *
 * <p>Implements {@link BeanFactoryPostProcessor} to register itself with the
 * {@link Environment}'s {@link ConfigurableConversionService} before any bean
 * property resolution occurs. This guarantees the converter is available when
 * Spring resolves {@code @Value} annotations targeting {@code ImageResource}.
 *
 * @see ImageResource
 */
@Slf4j
@Configuration
public class ImageResourceConverter implements Converter<String, ImageResource>, BeanFactoryPostProcessor {

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
        final Environment environment = beanFactory.getBean(Environment.class);
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            configurableEnvironment.getConversionService().addConverter(this);
        }
    }

    @Override
    public ImageResource convert(@NonNull final String source) {
        final Resource resource = resourceLoader.getResource(source);
        try (final InputStream is = resource.getInputStream()) {
            final BufferedImage image = ImageIO.read(is);
            if (image == null) {
                log.error("ImageIO returned null for resource: {}", source);
                return new ImageResource(new int[0], 0, 0);
            }
            final int width = image.getWidth();
            final int height = image.getHeight();
            final int[] rgbData = new int[width * height];
            image.getRGB(0, 0, width, height, rgbData, 0, width);
            return new ImageResource(rgbData, width, height);
        } catch (final IOException e) {
            log.error("Failed to load image resource: {}", source, e);
            return new ImageResource(new int[0], 0, 0);
        }
    }
}
