package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.component.ScrollableFlowPanel;
import house.x1337.app.smb3.ui.editor.component.WrapLayout;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.tile.edit.TileContextMenu;
import house.x1337.app.smb3.ui.editor.level.tile.edit.TileWindows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** The Tiles palette following an edited tile: its group, its thumbnail and its context menu. */
class TilePalettePanelShowTileTest {
    private final TileService tileService = mock(TileService.class);
    private final PaletteSelectionService paletteSelectionService = new PaletteSelectionService();
    private final Tile edited = tile(1042);
    private final Tile neighbour = tile(1043);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private TilePalettePanel palette;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ScrollableFlowPanel.class))
            .thenAnswer(invocation -> new ScrollableFlowPanel());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(WrapLayout.class))
            .thenAnswer(invocation -> new WrapLayout());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(paletteSelectionService);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(TileButton.class), any()))
            .thenAnswer(invocation -> new TileButton(invocation.getArgument(1)));
        when(tileService.getClassifiedTiles()).thenReturn(List.of(edited, neighbour));
        palette = new TilePalettePanel(
            tileService,
            mock(LevelSceneTabs.class),
            paletteSelectionService,
            new TileContextMenu(mock(TileWindows.class))
        );
        palette.init();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Every palette tile carries the context menu")
    void everyTileHasTheContextMenu() {
        // Execute & Verify
        assertThat(buttonsIn(SOLID)).allSatisfy(button -> assertThat(button.getComponentPopupMenu()).isNotNull());
    }

    @Test
    @DisplayName("A tile given another type moves to that type's group, and the group it leaves stays if not empty")
    void aNewTypeMovesTheTile() {
        // Prepare
        edited.setType(OBJECT_INTERACTIVE_SINGLE);

        // Execute
        palette.showTile(edited);

        // Verify
        assertThat(buttonsIn(SOLID)).extracting(TileButton::getTile).containsExactly(neighbour);
        assertThat(buttonsIn(OBJECT_INTERACTIVE_SINGLE)).extracting(TileButton::getTile).containsExactly(edited);
    }

    @Test
    @DisplayName("The last tile leaving a group drops the group")
    void anEmptiedGroupIsDropped() {
        // Prepare
        edited.setType(OBJECT_INTERACTIVE_SINGLE);
        neighbour.setType(OBJECT_INTERACTIVE_SINGLE);

        // Execute
        palette.showTile(edited);
        palette.showTile(neighbour);

        // Verify
        assertThat(palette.getGroups()).containsOnlyKeys(OBJECT_INTERACTIVE_SINGLE);
        assertThat(buttonsIn(OBJECT_INTERACTIVE_SINGLE)).hasSize(2);
    }

    @Test
    @DisplayName("A tile shown again in the same group gets a fresh button, so its new pixels show")
    void theSameTypeRedrawsTheTile() {
        // Prepare
        final TileButton before = buttonsIn(SOLID).getFirst();

        // Execute
        palette.showTile(edited);

        // Verify
        assertThat(buttonsIn(SOLID)).hasSize(2).doesNotContain(before);
    }

    @Test
    @DisplayName("A tile the palette did not show yet is added")
    void aNewTileIsAdded() {
        // Prepare
        final Tile resolved = tile(1044);

        // Execute
        palette.showTile(resolved);

        // Verify
        assertThat(buttonsIn(SOLID)).extracting(TileButton::getTile).contains(resolved);
    }

    private List<TileButton> buttonsIn(final TileType type) {
        final Component[] components = palette.getGroups().get(type).gridPanel().getComponents();
        return Arrays.stream(components).map(TileButton.class::cast).toList();
    }

    private Tile tile(final int id) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        return Tile.builder().id(id).type(SOLID).argbData(pixels).originalArgbData(pixels).build();
    }
}
