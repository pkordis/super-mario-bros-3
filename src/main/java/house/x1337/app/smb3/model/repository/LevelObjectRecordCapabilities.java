package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import org.jspecify.annotations.NonNull;

public sealed interface LevelObjectRecordCapabilities permits LevelObjectRecord {
    /**
     * Resolves the {@link LevelObjectType} from the record's type string and
     * instantiates a fresh {@link LevelObject} via the type's no-args constructor.
     * Resolution order: {@link LevelObjectTypeSingleTiled} → {@link LevelObjectTypeMultiTiled}.
     *
     * @throws IllegalArgumentException if the type string cannot be resolved to either enum.
     * @throws IllegalStateException if the resolved type's instance class cannot be instantiated.
     */
    default LevelObject toLevelObject() {
        final LevelObjectRecord record = (LevelObjectRecord) this;
        final LevelObjectType type = resolveLevelObjectType(record.getType());

        try {
            return type.getInstanceType().getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to instantiate " + type.getInstanceType().getName()
                    + " for record id=" + record.getId(),
                e
            );
        }
    }

    @NonNull
    private LevelObjectType resolveLevelObjectType(final String typeName) {
        LevelObjectType type = null;
        try {
            type = LevelObjectTypeSingleTiled.valueOf(typeName);
        } catch (final IllegalArgumentException ignored) {
            // not a single-tiled type - try multi-tiled below
        }
        if (type == null) {
            try {
                type = LevelObjectTypeMultiTiled.valueOf(typeName);
            } catch (final IllegalArgumentException ignored) {
                // not a multi-tiled type either
            }
        }
        if (type == null) {
            throw new IllegalArgumentException(
                "Cannot resolve LevelObjectType for type=\"" + typeName + "\""
            );
        }
        return type;
    }
}
