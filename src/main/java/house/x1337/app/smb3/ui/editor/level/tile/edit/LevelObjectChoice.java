package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;

/** An entry of the level object dropdown: {@code levelObject}, or, with none, {@link #NONE}. */
public record LevelObjectChoice(LevelObjectRecord levelObject) {
    public static final LevelObjectChoice NONE = new LevelObjectChoice(null);
    static final String NONE_LABEL = "- (none)";

    public boolean isNone() {
        return levelObject == null;
    }

    @Override
    public String toString() {
        if (isNone()) {
            return NONE_LABEL;
        }
        final String type = levelObject.findLevelObjectType()
            .map(LevelObjectType::getLabel)
            .orElse(levelObject.getType());
        final String description = levelObject.getDescription() == null ? "" : " - " + levelObject.getDescription();
        return type + " #" + levelObject.getId() + description;
    }
}
