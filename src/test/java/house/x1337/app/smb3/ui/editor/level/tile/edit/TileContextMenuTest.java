package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TileContextMenuTest {
    private final TileWindows tileWindows = mock(TileWindows.class);
    private final TileContextMenu tileContextMenu = new TileContextMenu(tileWindows);

    @Test
    @DisplayName("A palette tile's menu holds Edit only, which opens the edit window for that tile")
    void editOpensTheEditWindow() {
        // Prepare
        final Tile tile = Tile.builder().id(1001).type(SOLID).build();
        final JPopupMenu menu = tileContextMenu.forTile(tile);

        // Execute
        final JMenuItem edit = (JMenuItem) menu.getComponent(0);
        edit.doClick();

        // Verify
        assertThat(menu.getComponentCount()).isEqualTo(1);
        assertThat(edit.getText()).isEqualTo("Edit");
        verify(tileWindows).openEditor(tile);
    }
}
