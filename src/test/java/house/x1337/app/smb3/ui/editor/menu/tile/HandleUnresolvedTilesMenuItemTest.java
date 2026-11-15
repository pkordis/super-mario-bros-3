package house.x1337.app.smb3.ui.editor.menu.tile;

import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tile.edit.TileWindows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandleUnresolvedTilesMenuItemTest {
    private final TileService tileService = mock(TileService.class);
    private final TileWindows tileWindows = mock(TileWindows.class);
    private final HandleUnresolvedTilesMenuItem menuItem = new HandleUnresolvedTilesMenuItem(tileService, tileWindows);

    @BeforeEach
    void prepare() {
        menuItem.init();
    }

    @Test
    @DisplayName("Tiles > Handle Unresolved opens the review window with every tile without a type")
    void opensTheReviewWindow() {
        // Prepare
        final List<Tile> unresolved = List.of(Tile.builder().id(1001).build(), Tile.builder().id(1002).build());
        when(tileService.getUnresolvedTiles()).thenReturn(unresolved);

        // Execute
        menuItem.doClick();

        // Verify
        assertThat(menuItem.getText()).isEqualTo("Handle Unresolved...");
        verify(tileWindows).openReview(unresolved);
        verify(tileWindows, never()).sayNoUnresolvedTiles(any());
    }

    @Test
    @DisplayName("With every tile typed it says so instead of opening an empty window")
    void saysSoWhenNothingIsUnresolved() {
        // Prepare
        when(tileService.getUnresolvedTiles()).thenReturn(List.of());

        // Execute
        menuItem.doClick();

        // Verify
        verify(tileWindows).sayNoUnresolvedTiles(menuItem);
        verify(tileWindows, never()).openReview(any());
    }
}
