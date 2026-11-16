package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.model.repository.LevelObjectRecord;

import java.util.LinkedHashMap;
import java.util.Map;

/** What the author chose in the Associate with Existing Level Object section. */
public sealed interface LevelObjectAssociation {
    record Unlink() implements LevelObjectAssociation {
    }

    record Link(LevelObjectRecord target) implements LevelObjectAssociation {
    }

    /** Links the tile to a new copy of {@code source}, with the description and data the author gave it. */
    record Copy(LevelObjectRecord source, String description, Map<String, Object> data)
        implements LevelObjectAssociation {
        public LevelObjectRecord toRecord(final int id) {
            return LevelObjectRecord.builder()
                .id(id)
                .type(source.getType())
                .description(description)
                .data(new LinkedHashMap<>(data))
                .build();
        }
    }
}
