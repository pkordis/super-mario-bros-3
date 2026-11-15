package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

@Singleton
@RequiredArgsConstructor
public class TileContextMenu {
    private final TileWindows tileWindows;

    public JPopupMenu forTile(final Tile tile) {
        final JMenuItem edit = new JMenuItem("Edit");
        edit.addActionListener(event -> tileWindows.openEditor(tile));
        final JPopupMenu menu = new JPopupMenu();
        menu.add(edit);
        return menu;
    }
}
