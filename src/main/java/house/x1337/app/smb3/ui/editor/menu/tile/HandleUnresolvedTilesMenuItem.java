package house.x1337.app.smb3.ui.editor.menu.tile;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tile.edit.TileWindows;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;
import java.util.List;

@Singleton
@RequiredArgsConstructor
public class HandleUnresolvedTilesMenuItem extends JMenuItem {
    private final TileService tileService;
    private final TileWindows tileWindows;

    @PostConstruct
    void init() {
        setText("Handle Unresolved...");
        addActionListener(event -> handleUnresolved());
    }

    void handleUnresolved() {
        final List<Tile> unresolved = tileService.getUnresolvedTiles();
        if (unresolved.isEmpty()) {
            tileWindows.sayNoUnresolvedTiles(this);
        } else {
            tileWindows.openReview(unresolved);
        }
    }
}
