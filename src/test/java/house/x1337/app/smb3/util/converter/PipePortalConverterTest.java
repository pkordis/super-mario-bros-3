package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;

class PipePortalConverterTest {
    private static final String SCENE_ID = "scene-1";

    private final PipePortalConverter converter = new PipePortalConverter() {
    };

    @Test
    @DisplayName("Converting a record to a portal and back preserves every field")
    void roundTripsFromRecord() {
        // Prepare
        final PipePortalRecord original = PipePortalRecord.builder()
            .id("portal-1")
            .description("Warp to the bonus room")
            .type(IN_AND_OUT)
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .levelSceneId(SCENE_ID)
            .row(7)
            .column(12)
            .build();

        // Execute
        final PipePortal portal = converter.toPipePortal(original);
        final PipePortalRecord restored = converter.toPipePortalRecord(portal, SCENE_ID);

        // Verify
        assertThat(portal.getId()).isEqualTo("portal-1");
        assertThat(portal.getDescription()).isEqualTo("Warp to the bonus room");
        assertThat(portal.getType()).isEqualTo(IN_AND_OUT);
        assertThat(portal.getVirtualObjectType()).isEqualTo(PIPE_PORTAL_VERTICAL);
        assertThat(portal.getLevelSceneId()).isEqualTo(SCENE_ID);
        assertThat(portal.getRow()).isEqualTo(7);
        assertThat(portal.getColumn()).isEqualTo(12);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("The record takes the id of the scene being saved, not the portal's own")
    void recordTakesTheSavedSceneId() {
        // Prepare - placed before the scene's first save, so the portal has no scene id yet
        final PipePortal portal = PipePortal.builder()
            .type(IN_AND_OUT)
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .row(3)
            .column(4)
            .build();

        // Execute
        final PipePortalRecord record = converter.toPipePortalRecord(portal, SCENE_ID);

        // Verify
        assertThat(record.getLevelSceneId()).isEqualTo(SCENE_ID);
        assertThat(record.getId()).isEqualTo(portal.getId());
    }
}
