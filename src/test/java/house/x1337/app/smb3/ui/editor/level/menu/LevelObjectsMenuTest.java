package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.CreateCustomLevelObjectMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.CreateEnemyFromImageMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.EditInteractiveSingleTiledMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.PipePortalLinkageMenuItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LevelObjectsMenuTest {
    @Test
    @DisplayName("Pipe Portal Linkage is the last entry of Level Objects, after a separator")
    void pipePortalLinkageIsLast() {
        // Prepare
        final CreateCustomLevelObjectMenuItem createCustom = new CreateCustomLevelObjectMenuItem();
        final PipePortalLinkageMenuItem linkage = new PipePortalLinkageMenuItem();
        final LevelObjectsMenu menu = new LevelObjectsMenu(
            new EditInteractiveSingleTiledMenuItem(),
            createCustom,
            new CreateEnemyFromImageMenuItem(mock(PngFileImporter.class), mock(TileService.class)),
            linkage
        );

        // Execute
        menu.init();

        // Verify
        final int last = menu.getItemCount() - 1;
        assertThat(menu.getItem(last)).isSameAs(linkage);
        assertThat(menu.getItem(last - 1)).as("A separator").isNull();
        assertThat(menu.getItem(last - 2)).isSameAs(createCustom);
    }
}
