package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pipe portals travel between the editor tab and the {@link LevelScene} it is saved, tested and loaded as.
 */
class LevelSceneEditorTabFactoryPipePortalsTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        // LevelScene resolves its vibration collaborator in a field initialiser.
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneVibration.class))
            .thenReturn(mock(LevelSceneVibration.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Saving or testing a tab hands its portals to the scene, in a list of the scene's own")
    void portalsGoFromTheTabToTheScene() {
        // Prepare
        final List<PipePortal> portals = new ArrayList<>(List.of(portal()));
        final LevelSceneEditorTab tab = tab(portals);
        final LevelScene fresh = new LevelScene();
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(LevelScene.class)).thenReturn(fresh);

        // Execute
        final LevelScene scene = LevelSceneEditorTabFactory.fromLevelSceneEditor(tab);

        // Verify
        assertThat(scene.getPipePortals()).containsExactlyElementsOf(portals);
        assertThat(scene.getPipePortals()).as("Editing the tab later must not change the scene").isNotSameAs(portals);
    }

    @Test
    @DisplayName("A tab that was never saved keeps the scene's own id")
    void unsavedTabKeepsTheScenesId() {
        // Prepare
        final LevelSceneEditorTab tab = tab(new ArrayList<>());
        when(tab.getSceneId()).thenReturn(null);
        final LevelScene fresh = new LevelScene();
        final String generatedId = fresh.getId();
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(LevelScene.class)).thenReturn(fresh);

        // Execute
        final LevelScene scene = LevelSceneEditorTabFactory.fromLevelSceneEditor(tab);

        // Verify
        assertThat(scene.getId()).isNotNull().isEqualTo(generatedId);
    }

    @Test
    @DisplayName("Opening a scene hands its portals to the new tab, in a list of the tab's own")
    void portalsGoFromTheSceneToTheTab() {
        // Prepare
        final List<PipePortal> portals = new ArrayList<>(List.of(portal()));
        final LevelScene scene = LevelScene.builder()
            .id("scene-1")
            .dimensions(new LevelSceneDimensions(4, 4))
            .pipePortals(portals)
            .build();
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLevelSceneEditorGrid()).thenReturn(mock(LevelSceneEditorGrid.class));
        stubNewTab(tab);

        // Execute
        LevelSceneEditorTabFactory.fromScene(scene);

        // Verify
        final ArgumentCaptor<List<PipePortal>> captor = portalsCaptor();
        verify(tab).setPipePortals(captor.capture());
        assertThat(captor.getValue()).containsExactlyElementsOf(portals);
        assertThat(captor.getValue()).as("Editing the tab must not change the loaded scene").isNotSameAs(portals);
    }

    private void stubNewTab(final LevelSceneEditorTab tab) {
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneImportRouter.class))
            .thenReturn(mock(LevelSceneImportRouter.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TilePalettePanel.class))
            .thenReturn(mock(TilePalettePanel.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(mock(PaletteSelectionService.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(EnemyLevelSceneGridStamper.class))
            .thenReturn(mock(EnemyLevelSceneGridStamper.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorTab.class), any(), any(), any(), any()))
            .thenReturn(tab);
    }

    private static LevelSceneEditorTab tab(final List<PipePortal> portals) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLayers()).thenReturn(List.of());
        when(tab.getSceneId()).thenReturn("scene-1");
        when(tab.getColumns()).thenReturn(4);
        when(tab.getRows()).thenReturn(4);
        when(tab.getPipePortals()).thenReturn(portals);
        return tab;
    }

    private static PipePortal portal() {
        return PipePortal.builder()
            .type(IN)
            .virtualObjectType(PIPE_PORTAL_HORIZONTAL)
            .levelSceneId("scene-1")
            .row(3)
            .column(1)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<PipePortal>> portalsCaptor() {
        return ArgumentCaptor.forClass((Class<List<PipePortal>>) (Class<?>) List.class);
    }
}
