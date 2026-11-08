package house.x1337.app.smb3.service;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.PipePortalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Set;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipePortalServiceTest {
    private static final String SCENE_ID = "scene-1";

    private PipePortalRepository repository;
    private PipePortalService service;

    @BeforeEach
    void prepare() {
        repository = mock(PipePortalRepository.class);
        service = new PipePortalService(repository);
    }

    @Test
    @DisplayName("Replacing a scene's portals deletes the stored ones, then inserts the current ones under its id")
    void replaceAllDeletesThenInserts() {
        // Prepare
        final PipePortal horizontal = portal(PIPE_PORTAL_HORIZONTAL, 4, 2);
        final PipePortal vertical = portal(PIPE_PORTAL_VERTICAL, 6, 9);
        final ArgumentCaptor<List<PipePortalRecord>> inserted = recordsCaptor();

        // Execute
        service.replaceAll(SCENE_ID, List.of(horizontal, vertical));

        // Verify
        final InOrder order = inOrder(repository);
        order.verify(repository).deleteByLevelSceneId(SCENE_ID);
        order.verify(repository).insertAll(inserted.capture());
        assertThat(inserted.getValue())
            .extracting(PipePortalRecord::getId)
            .containsExactly(horizontal.getId(), vertical.getId());
        assertThat(inserted.getValue())
            .extracting(PipePortalRecord::getLevelSceneId)
            .containsOnly(SCENE_ID);
    }

    @Test
    @DisplayName("Replacing a scene's portals frees the partners of the stored ones that are gone, after inserting")
    void replaceAllFreesThePartnersOfRemovedPortals() {
        // Prepare
        final PipePortal kept = portal(PIPE_PORTAL_HORIZONTAL, 4, 2);
        final PipePortalRecord keptRecord = PipePortalRecord.builder().id(kept.getId()).levelSceneId(SCENE_ID).build();
        final PipePortalRecord removed = PipePortalRecord.builder().id("removed").levelSceneId(SCENE_ID).build();
        when(repository.findByLevelSceneId(SCENE_ID)).thenReturn(List.of(keptRecord, removed));

        // Execute
        service.replaceAll(SCENE_ID, List.of(kept));

        // Verify
        final InOrder order = inOrder(repository);
        order.verify(repository).deleteByLevelSceneId(SCENE_ID);
        order.verify(repository).insertAll(anyList());
        order.verify(repository).clearLinksTo(Set.of("removed"));
    }

    @Test
    @DisplayName("Saving a scene without portals clears the ones it had")
    void replaceAllWithNoPortalsClearsTheScene() {
        // Prepare
        final ArgumentCaptor<List<PipePortalRecord>> inserted = recordsCaptor();

        // Execute
        service.replaceAll(SCENE_ID, null);

        // Verify
        final InOrder order = inOrder(repository);
        order.verify(repository).deleteByLevelSceneId(SCENE_ID);
        order.verify(repository).insertAll(inserted.capture());
        assertThat(inserted.getValue()).isEmpty();
    }

    @Test
    @DisplayName("A scene's stored portals load into a list the editor can change")
    void findByLevelSceneIdReturnsAMutableList() {
        // Prepare
        final PipePortalRecord record = PipePortalRecord.builder()
            .id("portal-1")
            .type(OUT)
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .levelSceneId(SCENE_ID)
            .coordinates(of(9, 6))
            .build();
        when(repository.findByLevelSceneId(SCENE_ID)).thenReturn(List.of(record));

        // Execute
        final List<PipePortal> portals = service.findByLevelSceneId(SCENE_ID);
        portals.add(portal(PIPE_PORTAL_HORIZONTAL, 1, 1));

        // Verify
        assertThat(portals).hasSize(2);
        assertThat(portals.getFirst().getId()).isEqualTo("portal-1");
        assertThat(portals.getFirst().getType()).isEqualTo(OUT);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<PipePortalRecord>> recordsCaptor() {
        return ArgumentCaptor.forClass((Class<List<PipePortalRecord>>) (Class<?>) List.class);
    }

    private static PipePortal portal(final VirtualObjectType type, final int row, final int column) {
        return PipePortal.builder()
            .type(IN)
            .virtualObjectType(type)
            .coordinates(of(column, row))
            .build();
    }
}
