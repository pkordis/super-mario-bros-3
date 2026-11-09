package house.x1337.app.smb3.converter;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.converter.base.BaseValueConverter;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Singleton
public class AnimationImageResourceConverter extends BaseValueConverter<AnimationImageResource> {
    private static final Pattern IMG_BUNDLE_REGEX = Pattern.compile("(.*)\\{(\\d+),\\s*(\\d+)}(.*)");
    private static final Pattern ALTERNATIVES_REGEX = Pattern.compile("<([^<>]*)>");
    private static final String ALTERNATIVE_SEPARATOR = ",";

    private final ImageResourceConverter imageResourceConverter = new ImageResourceConverter();

    @Override
    public AnimationImageResource convert(@NonNull final String source) {
        final AnimationImageResource.AnimationImageResourceBuilder builder = AnimationImageResource.builder();
        paths(source).forEach(path -> builder.imageResource(imageResourceConverter.convert(path)));
        return builder.build();
    }

    static List<String> paths(@NonNull final String source) {
        final List<String> paths = new ArrayList<>();
        expandAlternatives(source).forEach(path -> paths.addAll(expandFrameNumbers(path)));
        return paths;
    }

    private static List<String> expandAlternatives(final String source) {
        final Matcher matcher = ALTERNATIVES_REGEX.matcher(source);
        if (!matcher.find()) {
            return List.of(source);
        }
        final String head = source.substring(0, matcher.start());
        final List<String> tails = expandAlternatives(source.substring(matcher.end()));
        final List<String> paths = new ArrayList<>();
        for (final String alternative : matcher.group(1).split(ALTERNATIVE_SEPARATOR, -1)) {
            final String trimmed = alternative.strip();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(format("Empty alternative in <%s>: %s", matcher.group(1), source));
            }
            tails.forEach(tail -> paths.add(head + trimmed + tail));
        }
        return paths;
    }

    private static List<String> expandFrameNumbers(final String path) {
        final Matcher matcher = IMG_BUNDLE_REGEX.matcher(path);
        if (!matcher.matches()) {
            return List.of(path);
        }
        final String name = matcher.group(1);
        final int start = Integer.parseInt(matcher.group(2));
        final int end = Integer.parseInt(matcher.group(3));
        final String extension = matcher.group(4);
        final List<String> paths = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            paths.add(format("%s%d%s", name, i, extension));
        }
        return paths;
    }
}
