package house.x1337.app.smb3.ui.editor.level.menu.scene.level;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Activation is funnelled through this menu from wherever it is requested — the menu itself, or the Tiles
 * palette when a tile belonging to another layer is picked — so the three things that have to move
 * together always do: the tab's active layer, the menu's own selection, and both palettes.
 */
class ActiveLayerMenuActivationTest {
    private LevelSceneEditorTabSystem tabSystem;
    private EnemiesPalettePanel enemiesPalettePanel;
    private TilePalettePanel tilePalettePanel;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        tabSystem = mock(LevelSceneEditorTabSystem.class);
        enemiesPalettePanel = mock(EnemiesPalettePanel.class);
        tilePalettePanel = mock(TilePalettePanel.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TilePalettePanel.class))
            .thenReturn(tilePalettePanel);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Activating a layer makes it the tab's active one")
    void activatingMovesTheTabsActiveLayer() {
        // Prepare
        final LevelSceneLayer air = layer(AIR);
        final LevelSceneLayer interactive = layer(INTERACTIVE_OBJECTS);
        final LevelSceneEditorTab tab = tabWith(air, air, interactive);

        // Execute
        menu().activate(INTERACTIVE_OBJECTS);

        // Verify
        verify(tab).setActiveLayer(interactive);
    }

    @Test
    @DisplayName("Both palettes are re-gated, so no stale selection survives the switch")
    void bothPalettesAreResynced() {
        // Prepare
        final LevelSceneLayer air = layer(AIR);
        final LevelSceneLayer npc = layer(NON_PLAYABLE_CHARACTERS);
        tabWith(air, air, npc);

        // Execute
        menu().activate(NON_PLAYABLE_CHARACTERS);

        // Verify
        verify(enemiesPalettePanel).syncEnabledState();
        verify(tilePalettePanel).syncEnabledState();
    }

    @Test
    @DisplayName("Asking for a layer the scene does not have leaves the active one alone")
    void aMissingLayerIsNotActivated() {
        // Prepare - a blank scene starts without an NPC layer
        final LevelSceneLayer air = layer(AIR);
        final LevelSceneEditorTab tab = tabWith(air, air);

        // Execute
        menu().activate(NON_PLAYABLE_CHARACTERS);

        // Verify
        verify(tab, never()).setActiveLayer(any());
    }

    @Test
    @DisplayName("With no tab open nothing is touched")
    void withoutATabNothingHappens() {
        // Prepare
        when(tabSystem.getActiveTab()).thenReturn(null);

        // Execute
        menu().activate(INTERACTIVE_OBJECTS);

        // Verify
        verify(enemiesPalettePanel, never()).syncEnabledState();
        verify(tilePalettePanel, never()).syncEnabledState();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** The menu without its {@code @PostConstruct} build-out, which needs no radio items to activate. */
    private ActiveLayerMenu menu() {
        return new ActiveLayerMenu(tabSystem, enemiesPalettePanel);
    }

    private LevelSceneEditorTab tabWith(
        final LevelSceneLayer activeLayer,
        final LevelSceneLayer... layers
    ) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLayers()).thenReturn(List.of(layers));
        when(tab.getActiveLayer()).thenReturn(activeLayer);
        when(tabSystem.getActiveTab()).thenReturn(tab);
        return tab;
    }

    private static LevelSceneLayer layer(final LevelSceneLayerType type) {
        return LevelSceneLayer.builder().type(type).visible(true).build();
    }
}
