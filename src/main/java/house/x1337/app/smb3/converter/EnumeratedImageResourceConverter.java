package house.x1337.app.smb3.converter;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.converter.base.BaseValueConverter;
import house.x1337.app.smb3.enumeration.resource.EnumeratedImageResourceType;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.util.CastCapable;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Singleton
public class EnumeratedImageResourceConverter<E extends EnumeratedImageResourceType>
    extends BaseValueConverter<EnumeratedImageResource<E>>
    implements CastCapable {
    private final ImageResourceConverter imageResourceConverter = new ImageResourceConverter();

    @Override
    @SneakyThrows
    public EnumeratedImageResource<E> convert(@NonNull final String source) {
        final Class<?> enumType = Class.forName(source);
        if (enumType.isEnum() && EnumeratedImageResourceType.class.isAssignableFrom(enumType)) {
            final EnumeratedImageResourceType[] constants = (EnumeratedImageResourceType[]) enumType.getEnumConstants();
            final Map<E, ImageResource> imageResources = Arrays
                .stream(constants)
                .map(this::<E>checkedCast)
                .collect(
                    toMap(
                        identity(),
                        e -> imageResourceConverter.convert(e.getPath())
                    )
                );
            return new EnumeratedImageResource<>(imageResources);
        }
        throw new IllegalArgumentException(source + " must be an EnumeratedImageResource type");
    }
}
