package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.config.db.NitriteJacksonMapper;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import org.dizitart.no2.Nitrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.dizitart.no2.common.module.NitriteModule.module;

/**
 * Runs the Nitrite repository against an in-memory database with the production Jackson mapper, so the record's
 * enum and primitive fields are checked through the real mapping.
 */
class NitritePipePortalRepositoryTest {
    private static final String SCENE_ID = "scene-1";
    private static final String OTHER_SCENE_ID = "scene-2";

    private Nitrite nitrite;
    private NitritePipePortalRepository repository;

    @BeforeEach
    void prepare() {
        nitrite = Nitrite.builder()
            .loadModule(module(new NitriteJacksonMapper()))
            .openOrCreate();
        repository = new NitritePipePortalRepository(nitrite.getRepository(PipePortalRecord.class));
    }

    @AfterEach
    void tearDown() {
        nitrite.close();
    }

    @Test
    @DisplayName("Stored portals come back with every field, and only for their own scene")
    void findsTheStoredPortalsOfAScene() {
        // Prepare
        final PipePortalRecord horizontal = record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2);
        final PipePortalRecord vertical = record("portal-2", SCENE_ID, OUT, PIPE_PORTAL_VERTICAL, 9, 7);
        final PipePortalRecord elsewhere = record("portal-3", OTHER_SCENE_ID, IN_AND_OUT, PIPE_PORTAL_VERTICAL, 1, 1);

        // Execute
        repository.insertAll(List.of(horizontal, vertical, elsewhere));
        final List<PipePortalRecord> found = repository.findByLevelSceneId(SCENE_ID);

        // Verify
        assertThat(found).containsExactlyInAnyOrder(horizontal, vertical);
    }

    @Test
    @DisplayName("Deleting a scene's portals leaves the other scenes' portals alone")
    void deleteByLevelSceneIdOnlyDeletesThatScene() {
        // Prepare
        final PipePortalRecord elsewhere = record("portal-3", OTHER_SCENE_ID, IN_AND_OUT, PIPE_PORTAL_VERTICAL, 1, 1);
        repository.insertAll(List.of(
            record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2),
            elsewhere
        ));

        // Execute
        repository.deleteByLevelSceneId(SCENE_ID);

        // Verify
        assertThat(repository.findByLevelSceneId(SCENE_ID)).isEmpty();
        assertThat(repository.findByLevelSceneId(OTHER_SCENE_ID)).containsExactly(elsewhere);
    }

    @Test
    @DisplayName("Inserting no portals writes nothing")
    void insertingNothingWritesNothing() {
        // Execute
        repository.insertAll(List.of());

        // Verify
        assertThat(repository.findByLevelSceneId(SCENE_ID)).isEmpty();
    }

    private static PipePortalRecord record(
        final String id,
        final String levelSceneId,
        final PipePortalType type,
        final VirtualObjectType virtualObjectType,
        final int row,
        final int column
    ) {
        return PipePortalRecord.builder()
            .id(id)
            .description("Portal " + id)
            .type(type)
            .virtualObjectType(virtualObjectType)
            .levelSceneId(levelSceneId)
            .row(row)
            .column(column)
            .build();
    }
}
