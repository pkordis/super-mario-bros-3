package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.app.ApplicationTerminator;
import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.service.GameSettingsService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.menu.file.BrowseLevelScenesMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.file.ExitMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.file.ImportFromPngLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.file.NewLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.file.SaveLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.file.SettingsMenuItem;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JPopupMenu;
import java.awt.Component;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The File menu's items, in order: Settings... sits in its own group just above Exit.
 */
class FileMenuTest {
    @Test
    @DisplayName("The File menu holds Settings... in its own group above Exit")
    void settingsSitsAboveExit() {
        // Prepare
        final SettingsMenuItem settings = new SettingsMenuItem(mock(GameSettingsService.class));
        final ExitMenuItem exit = new ExitMenuItem(mock(ApplicationTerminator.class));
        final FileMenu fileMenu = new FileMenu(
            new NewLevelSceneMenuItem(),
            new SaveLevelSceneMenuItem(mock(LevelSceneEditorTabSystem.class)),
            new BrowseLevelScenesMenuItem(),
            new ImportFromPngLevelSceneMenuItem(
                mock(LevelSceneEditorTabSystem.class),
                mock(PngFileImporter.class),
                mock(TileService.class)
            ),
            settings,
            exit
        );

        // Execute
        fileMenu.init();

        // Verify
        final List<Component> items = Arrays.asList(fileMenu.getMenuComponents());
        assertThat(items).hasSize(9);
        assertThat(items.get(5)).as("Separator before Settings...").isInstanceOf(JPopupMenu.Separator.class);
        assertThat(items.get(6)).isSameAs(settings);
        assertThat(items.get(7)).as("Separator before Exit").isInstanceOf(JPopupMenu.Separator.class);
        assertThat(items.get(8)).isSameAs(exit);
    }
}
