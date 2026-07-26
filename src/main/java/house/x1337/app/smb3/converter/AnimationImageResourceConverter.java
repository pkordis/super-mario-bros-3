package house.x1337.app.smb3.converter;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.converter.base.BaseValueConverter;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Singleton
public class AnimationImageResourceConverter extends BaseValueConverter<AnimationImageResource> {
    private static final Pattern IMG_BUNDLE_REGEX = Pattern.compile("(.*)\\{(\\d+),\\s*(\\d+)}(.*)");

    private final ImageResourceConverter imageResourceConverter = new ImageResourceConverter();

    @Override
    public AnimationImageResource convert(@NonNull final String source) {
        final Matcher matcher = IMG_BUNDLE_REGEX.matcher(source);
        if (matcher.matches()) {
            final String name = matcher.group(1);
            final int start = Integer.parseInt(matcher.group(2));
            final int end = Integer.parseInt(matcher.group(3));
            final String extension = matcher.group(4);
            final AnimationImageResource.AnimationImageResourceBuilder builder = AnimationImageResource.builder();
            for (int i = start; i <= end; i++) {
                final String filename = format("%s%d%s", name, i, extension);
                builder.imageResource(imageResourceConverter.convert(filename));
            }
            return builder.build();
        }
        throw new IllegalArgumentException("Value supplied does not conform to " + IMG_BUNDLE_REGEX);
    }
}
