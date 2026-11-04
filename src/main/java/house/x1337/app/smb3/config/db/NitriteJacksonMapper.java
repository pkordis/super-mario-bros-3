package house.x1337.app.smb3.config.db;

import org.dizitart.no2.mapper.jackson.JacksonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.ConstructorDetector;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nitrite's Jackson mapper with the Jackson 2 creator and null-handling rules that Nitrite 4.3.2 used.
 * <p>
 * Since 4.3.3, {@code nitrite-jackson-mapper} runs on Jackson 3, which changed two defaults that break the Lombok
 * records in {@code model.repository}:
 * <ul>
 *     <li>A public all-args constructor whose parameters have names (the build uses {@code -parameters}) is chosen
 *     as the creator even when a no-arg constructor exists. Fields missing from a stored document then reach the
 *     constructor as {@code null}, which loses {@code @Builder.Default} initializers.</li>
 *     <li>{@link DeserializationFeature#FAIL_ON_NULL_FOR_PRIMITIVES} is enabled, so a missing or {@code null} value
 *     for a primitive field fails. Nitrite builds every entity from an empty document when it opens a repository,
 *     so this fails {@code getRepository} for any record with a primitive field.</li>
 * </ul>
 * This mapper turns both back, so entities are built with the no-arg constructor and fields are set by reflection.
 */
public class NitriteJacksonMapper extends JacksonMapper {
    private ObjectMapper objectMapper;

    @Override
    protected ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = ((JsonMapper) super.getObjectMapper()).rebuild()
                .constructorDetector(ConstructorDetector.DEFAULT.withAllowImplicitWithDefaultConstructor(false))
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        }
        return objectMapper;
    }
}
