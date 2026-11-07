package house.x1337.app.smb3.service;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.repository.LevelSceneRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A scene's pipe portals live in their own collection, so every load, save and delete of a scene has to carry its
 * portals with it.
 */
class LevelSceneServicePipePortalsTest {
    private static final String SCENE_ID = "scene-1";
    private static final String OTHER_SCENE_ID = "scene-2";

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelSceneRepository levelSceneRepository;
    private PipePortalService pipePortalService;
    private LevelSceneService service;

    @BeforeEach
    void prepare() {
        // LevelScene resolves its vibration collaborator in a field initialiser.
        staticBeanFactory = mockStaticBeanFactory();
        levelSceneRepository = mock(LevelSceneRepository.class);
        pipePortalService = mock(PipePortalService.class);
        service = new LevelSceneService(levelSceneRepository, mock(TileService.class), pipePortalService);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Saving a scene stores its portals under the saved scene's id, after the scene itself")
    void saveReplacesThePortalsOfTheScene() {
        // Prepare
        final List<PipePortal> portals = List.of(portal(PIPE_PORTAL_HORIZONTAL, 4, 2));
        final LevelScene scene = LevelScene.builder()
            .id(SCENE_ID)
            .title("1-1")
            .dimensions(new LevelSceneDimensions(4, 5))
            .pipePortals(portals)
            .build();

        // Execute
        service.save(scene);

        // Verify
        final InOrder order = inOrder(levelSceneRepository, pipePortalService);
        order.verify(levelSceneRepository).upsert(any(LevelSceneRecord.class));
        order.verify(pipePortalService).replaceAll(SCENE_ID, portals);
    }

    @Test
    @DisplayName("The last edited scene loads with its portals")
    void findLastEditedAttachesThePortals() {
        // Prepare
        final List<PipePortal> portals = new ArrayList<>(List.of(portal(PIPE_PORTAL_VERTICAL, 3, 1)));
        when(levelSceneRepository.findLastEdited()).thenReturn(Optional.of(record(SCENE_ID)));
        when(pipePortalService.findByLevelSceneId(SCENE_ID)).thenReturn(portals);

        // Execute
        final LevelScene scene = service.findLastEdited().orElseThrow();

        // Verify
        assertThat(scene.getPipePortals()).containsExactlyElementsOf(portals);
    }

    @Test
    @DisplayName("Every scene of a browse page loads with its own portals")
    void findPageAttachesThePortalsOfEachScene() {
        // Prepare
        final PipePortal first = portal(PIPE_PORTAL_HORIZONTAL, 2, 0);
        final PipePortal second = portal(PIPE_PORTAL_VERTICAL, 3, 1);
        when(levelSceneRepository.findPage(0, 10)).thenReturn(List.of(record(SCENE_ID), record(OTHER_SCENE_ID)));
        when(pipePortalService.findByLevelSceneId(SCENE_ID)).thenReturn(new ArrayList<>(List.of(first)));
        when(pipePortalService.findByLevelSceneId(OTHER_SCENE_ID)).thenReturn(new ArrayList<>(List.of(second)));

        // Execute
        final List<LevelScene> page = service.findPage(0, 10);

        // Verify
        assertThat(page).hasSize(2);
        assertThat(page.get(0).getPipePortals()).containsExactly(first);
        assertThat(page.get(1).getPipePortals()).containsExactly(second);
    }

    @Test
    @DisplayName("A scene loaded without stored portals has an empty list, not null")
    void sceneWithoutPortalsLoadsAnEmptyList() {
        // Prepare
        when(levelSceneRepository.findLastEdited()).thenReturn(Optional.of(record(SCENE_ID)));
        when(pipePortalService.findByLevelSceneId(SCENE_ID)).thenReturn(new ArrayList<>());

        // Execute
        final LevelScene scene = service.findLastEdited().orElseThrow();

        // Verify
        assertThat(scene.getPipePortals()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Deleting a scene deletes its portals too")
    void deleteCascadesToThePortals() {
        // Execute
        service.delete(SCENE_ID);

        // Verify
        verify(levelSceneRepository).delete(SCENE_ID);
        verify(pipePortalService).deleteByLevelSceneId(SCENE_ID);
    }

    @Test
    @DisplayName("A scene found by its id loads with its portals")
    void findByIdAttachesThePortals() {
        // Prepare
        final List<PipePortal> portals = new ArrayList<>(List.of(portal(PIPE_PORTAL_VERTICAL, 3, 1)));
        when(levelSceneRepository.findById(SCENE_ID)).thenReturn(Optional.of(record(SCENE_ID)));
        when(pipePortalService.findByLevelSceneId(SCENE_ID)).thenReturn(portals);

        // Execute
        final LevelScene scene = service.findById(SCENE_ID).orElseThrow();

        // Verify
        assertThat(scene.getId()).isEqualTo(SCENE_ID);
        assertThat(scene.getPipePortals()).containsExactlyElementsOf(portals);
    }

    @Test
    @DisplayName("An unknown scene id finds nothing")
    void findByIdOfAnUnknownSceneIsEmpty() {
        // Prepare
        when(levelSceneRepository.findById("missing")).thenReturn(Optional.empty());

        // Execute & Verify
        assertThat(service.findById("missing")).isEmpty();
    }

    @Test
    @DisplayName("Scene titles come straight from the repository, without loading any portals")
    void findTitlesByIdDoesNotLoadPortals() {
        // Prepare
        when(levelSceneRepository.findTitlesById()).thenReturn(Map.of(SCENE_ID, "1-1"));

        // Execute
        final Map<String, String> titles = service.findTitlesById();

        // Verify
        assertThat(titles).containsExactly(entry(SCENE_ID, "1-1"));
        verifyNoInteractions(pipePortalService);
    }

    private static MockedStatic<StaticBeanFactory> mockStaticBeanFactory() {
        final MockedStatic<StaticBeanFactory> mocked = mockStatic(StaticBeanFactory.class);
        mocked
            .when(() -> StaticBeanFactory.getBean(LevelSceneVibration.class))
            .thenReturn(mock(LevelSceneVibration.class));
        return mocked;
    }

    private static LevelSceneRecord record(final String id) {
        return LevelSceneRecord.builder()
            .id(id)
            .title("Scene " + id)
            .rows(5)
            .columns(4)
            .build();
    }

    private static PipePortal portal(final VirtualObjectType type, final int row, final int column) {
        return PipePortal.builder()
            .type(type == PIPE_PORTAL_HORIZONTAL ? IN : OUT)
            .virtualObjectType(type)
            .levelSceneId(SCENE_ID)
            .row(row)
            .column(column)
            .build();
    }
}
