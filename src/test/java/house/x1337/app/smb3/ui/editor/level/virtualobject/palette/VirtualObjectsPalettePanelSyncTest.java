package house.x1337.app.smb3.ui.editor.level.virtualobject.palette;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ScrollableFlowPanel;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.WrapLayout;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * How the Virtual Objects palette gates its stamps against the active tab, the same way the Enemies palette gates its
 * entries: placeable on the NPC layer, greyed but clickable on another layer, and disabled when there is no NPC layer.
 */
class VirtualObjectsPalettePanelSyncTest {
    private LevelSceneEditorTabSystem tabSystem;
    private PaletteSelectionService paletteSelectionService;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        tabSystem = mock(LevelSceneEditorTabSystem.class);
        paletteSelectionService = new PaletteSelectionService();
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(paletteSelectionService);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ScrollableFlowPanel.class))
            .thenAnswer(invocation -> new ScrollableFlowPanel());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(WrapLayout.class))
            .thenAnswer(invocation -> new WrapLayout());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(VirtualObjectButton.class))
            .thenAnswer(invocation -> new VirtualObjectButton());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The palette holds one stamp per virtual object type")
    void oneStampPerType() {
        // Prepare
        final LevelSceneLayer air = layer(AIR);
        tabWith(air, air, layer(NON_PLAYABLE_CHARACTERS));

        // Execute
        final VirtualObjectsPalettePanel panel = panel();

        // Verify
        assertThat(panel.getButtonsByType()).containsOnlyKeys(VirtualObjectType.values());
    }

    @Test
    @DisplayName("Off the NPC layer a stamp is greyed out but stays enabled, and the hint says a click switches")
    void offTheNpcLayerStampsAreOutOfLayerButEnabled() {
        // Prepare
        final LevelSceneLayer air = layer(AIR);
        tabWith(air, air, layer(NON_PLAYABLE_CHARACTERS));

        // Execute
        final VirtualObjectsPalettePanel panel = panel();

        // Verify
        final VirtualObjectButton button = panel.getButtonsByType().get(PIPE_PORTAL_HORIZONTAL);
        assertThat(button.isOutOfActiveLayer()).isTrue();
        assertThat(button.isEnabled()).isTrue();
        assertThat(panel.getHintLabel().getText()).contains("switches to it");
        assertThat(panel.getHintLabel().isVisible()).isTrue();
    }

    @Test
    @DisplayName("In a scene with no NPC layer every stamp is disabled")
    void withoutAnNpcLayerStampsAreDisabled() {
        // Prepare
        final LevelSceneLayer air = layer(AIR);
        tabWith(air, air, layer(DECORATIONS_LAND));

        // Execute
        final VirtualObjectsPalettePanel panel = panel();

        // Verify
        final VirtualObjectButton button = panel.getButtonsByType().get(PIPE_PORTAL_HORIZONTAL);
        assertThat(button.isEnabled()).isFalse();
        assertThat(button.isOutOfActiveLayer()).isFalse();
        assertThat(panel.getHintLabel().getText()).contains("has no");
    }

    @Test
    @DisplayName("On the NPC layer a stamp is plainly placeable and the hint is out of the way")
    void onTheNpcLayerStampsArePlaceable() {
        // Prepare
        final LevelSceneLayer npc = layer(NON_PLAYABLE_CHARACTERS);
        tabWith(npc, layer(AIR), npc);

        // Execute
        final VirtualObjectsPalettePanel panel = panel();

        // Verify
        final VirtualObjectButton button = panel.getButtonsByType().get(PIPE_PORTAL_HORIZONTAL);
        assertThat(button.isEnabled()).isTrue();
        assertThat(button.isOutOfActiveLayer()).isFalse();
        assertThat(panel.getHintLabel().isVisible()).isFalse();
    }

    @Test
    @DisplayName("Moving the active layer away from NPCs releases an armed stamp")
    void leavingTheNpcLayerReleasesTheArmedStamp() {
        // Prepare
        final LevelSceneLayer npc = layer(NON_PLAYABLE_CHARACTERS);
        final LevelSceneLayer air = layer(AIR);
        final LevelSceneEditorTab tab = tabWith(npc, air, npc);
        final VirtualObjectsPalettePanel panel = panel();
        final VirtualObjectButton button = panel.getButtonsByType().get(PIPE_PORTAL_HORIZONTAL);
        paletteSelectionService.select(button);
        when(tab.getActiveLayer()).thenReturn(air);

        // Execute - as ActiveLayerMenu.activate does after a layer switch
        panel.syncEnabledState();

        // Verify
        assertThat(paletteSelectionService.hasSelection()).isFalse();
        assertThat(button.isSelected()).isFalse();
        assertThat(button.isOutOfActiveLayer()).isTrue();
    }

    /** The panel built as Spring builds it: constructed, then initialised. */
    private VirtualObjectsPalettePanel panel() {
        final VirtualObjectsPalettePanel panel = new VirtualObjectsPalettePanel(paletteSelectionService, tabSystem);
        panel.init();
        return panel;
    }

    private LevelSceneEditorTab tabWith(final LevelSceneLayer activeLayer, final LevelSceneLayer... layers) {
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
