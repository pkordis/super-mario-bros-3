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

    @Test
    @DisplayName("A portal is found by its id with every field, and an unknown id finds nothing")
    void findsAPortalById() {
        // Prepare
        final PipePortalRecord stored = record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2);
        stored.setLinksToId("portal-9");
        repository.insertAll(List.of(stored));

        // Execute & Verify
        assertThat(repository.findById("portal-1")).contains(stored);
        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    @DisplayName("Every stored portal is listed, whatever its scene")
    void findsEveryPortal() {
        // Prepare
        final PipePortalRecord first = record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2);
        final PipePortalRecord second = record("portal-2", OTHER_SCENE_ID, OUT, PIPE_PORTAL_VERTICAL, 9, 7);
        repository.insertAll(List.of(first, second));

        // Execute
        final List<PipePortalRecord> found = repository.findAll();

        // Verify
        assertThat(found).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("A portal's link is set and cleared without touching its other fields")
    void updatesTheLinkOfOnePortal() {
        // Prepare
        final PipePortalRecord stored = record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2);
        final PipePortalRecord other = record("portal-2", OTHER_SCENE_ID, OUT, PIPE_PORTAL_VERTICAL, 9, 7);
        repository.insertAll(List.of(stored, other));

        // Execute
        repository.updateLinksToId("portal-1", "portal-2");

        // Verify
        final PipePortalRecord linked = repository.findById("portal-1").orElseThrow();
        assertThat(linked.getLinksToId()).isEqualTo("portal-2");
        assertThat(linked.getLevelSceneId()).isEqualTo(SCENE_ID);
        assertThat(linked.getType()).isEqualTo(IN);
        assertThat(repository.findById("portal-2").orElseThrow().getLinksToId())
            .as("The other portal is not changed")
            .isNull();

        // Execute
        repository.updateLinksToId("portal-1", null);

        // Verify
        assertThat(repository.findById("portal-1").orElseThrow()).isEqualTo(stored);
    }

    @Test
    @DisplayName("Clearing links to some portals frees only the portals that pointed at them")
    void clearsOnlyTheMatchingLinks() {
        // Prepare
        final PipePortalRecord toGone = record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2);
        toGone.setLinksToId("gone-1");
        final PipePortalRecord toOtherGone = record("portal-2", OTHER_SCENE_ID, OUT, PIPE_PORTAL_VERTICAL, 9, 7);
        toOtherGone.setLinksToId("gone-2");
        final PipePortalRecord toKept = record("portal-3", OTHER_SCENE_ID, IN_AND_OUT, PIPE_PORTAL_VERTICAL, 1, 1);
        toKept.setLinksToId("kept");
        repository.insertAll(List.of(toGone, toOtherGone, toKept));

        // Execute
        repository.clearLinksTo(List.of("gone-1", "gone-2"));

        // Verify
        assertThat(repository.findById("portal-1").orElseThrow().getLinksToId()).isNull();
        assertThat(repository.findById("portal-2").orElseThrow().getLinksToId()).isNull();
        assertThat(repository.findById("portal-3").orElseThrow().getLinksToId()).isEqualTo("kept");
    }

    @Test
    @DisplayName("Clearing links to no portals changes nothing")
    void clearingNoLinksChangesNothing() {
        // Prepare
        final PipePortalRecord linked = record("portal-1", SCENE_ID, IN, PIPE_PORTAL_HORIZONTAL, 4, 2);
        linked.setLinksToId("portal-2");
        repository.insertAll(List.of(linked));

        // Execute
        repository.clearLinksTo(List.of());

        // Verify
        assertThat(repository.findById("portal-1").orElseThrow().getLinksToId()).isEqualTo("portal-2");
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
