package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
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
            .coordinates(of(12, 7))
            .linksToId("portal-2")
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
        assertThat(portal.getCoordinates()).isEqualTo(of(12, 7));
        assertThat(portal.getLinksToId()).isEqualTo("portal-2");
        assertThat(portal.isLinked()).isTrue();
        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("A free portal stays free through the conversion both ways")
    void roundTripsAFreePortal() {
        // Prepare
        final PipePortalRecord original = PipePortalRecord.builder()
            .id("portal-1")
            .type(IN_AND_OUT)
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .levelSceneId(SCENE_ID)
            .build();

        // Execute
        final PipePortal portal = converter.toPipePortal(original);
        final PipePortalRecord restored = converter.toPipePortalRecord(portal, SCENE_ID);

        // Verify
        assertThat(portal.getLinksToId()).isNull();
        assertThat(portal.isLinked()).isFalse();
        assertThat(restored.getLinksToId()).isNull();
    }

    @Test
    @DisplayName("The record takes the id of the scene being saved, not the portal's own")
    void recordTakesTheSavedSceneId() {
        // Prepare - placed before the scene's first save, so the portal has no scene id yet
        final PipePortal portal = PipePortal.builder()
            .type(IN_AND_OUT)
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .coordinates(of(4, 3))
            .build();

        // Execute
        final PipePortalRecord record = converter.toPipePortalRecord(portal, SCENE_ID);

        // Verify
        assertThat(record.getLevelSceneId()).isEqualTo(SCENE_ID);
        assertThat(record.getId()).isEqualTo(portal.getId());
    }
}
