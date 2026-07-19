package house.x1337.app.smb3.ui.editor.level.browse.core;

import house.x1337.app.smb3.game.LevelScene;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.table.DefaultTableModel;
import java.util.List;

public interface LevelScenesTableOwner {
    DefaultTableModel getTableModel();
    List<LevelScene> getLoadedLevelScenes();
    void onFieldValueChangeRequest(LevelScene levelScene, Field field, String newValue);
    void onRowSelected(int viewRow);

    @Getter
    @RequiredArgsConstructor
    enum Field {
        DESCRIPTION(2, "Description"),
        TITLE(1, "Title");

        private final int columnIndex;
        private final String label;

        public static Field fromColumnIndex(int columnIndex) {
            for (final Field field : Field.values()) {
                if (field.getColumnIndex() == columnIndex) {
                    return field;
                }
            }
            return null;
        }
    }
}
