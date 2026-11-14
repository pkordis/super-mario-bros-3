package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.app.ApplicationTerminator;
import house.x1337.app.smb3.service.GameSettingsService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.menu.LevelMenu;
import house.x1337.app.smb3.ui.editor.level.menu.LevelObjectsMenu;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneMenu;
import house.x1337.app.smb3.ui.editor.menu.file.ExitMenuItem;
import house.x1337.app.smb3.ui.editor.menu.file.SettingsMenuItem;
import house.x1337.app.smb3.ui.editor.menu.tile.ConfigureVirtualTilesMenuItem;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EditorMenuBarTest {
    @Test
    @DisplayName("The menu bar holds File, Tiles and Level, in that order")
    void fileTilesLevel() {
        // Prepare
        final EditorTabSystem editorTabSystem = mock(EditorTabSystem.class);
        when(editorTabSystem.getActiveTab()).thenReturn(Optional.empty());
        final FileMenu fileMenu = new FileMenu(
            editorTabSystem,
            List.of(),
            new SettingsMenuItem(mock(GameSettingsService.class)),
            new ExitMenuItem(mock(ApplicationTerminator.class))
        );
        fileMenu.init();
        final TilesMenu tilesMenu = new TilesMenu(new ConfigureVirtualTilesMenuItem(mock(TileService.class)));
        tilesMenu.init();
        final LevelMenu levelMenu = new LevelMenu(mock(LevelSceneMenu.class), mock(LevelObjectsMenu.class));
        final EditorMenuBar menuBar = new EditorMenuBar(fileMenu, tilesMenu, levelMenu);

        // Execute
        menuBar.init();

        // Verify
        assertThat(menuBar.getMenuCount()).isEqualTo(3);
        assertThat(menuBar.getMenu(0)).isSameAs(fileMenu);
        assertThat(menuBar.getMenu(0).getText()).isEqualTo("File");
        assertThat(menuBar.getMenu(1)).isSameAs(tilesMenu);
        assertThat(menuBar.getMenu(1).getText()).isEqualTo("Tiles");
        assertThat(menuBar.getMenu(2)).isSameAs(levelMenu);
    }
}
