package house.x1337.app.smb3.service;

import house.x1337.app.smb3.config.db.NitriteJacksonMapper;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.nitrite.NitritePipePortalRepository;
import org.dizitart.no2.Nitrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.dizitart.no2.common.module.NitriteModule.module;

/**
 * Linking, unlinking and the link integrity of saves and deletes, run through the real service and Nitrite
 * repository on an in-memory database, so what is checked is what ends up stored.
 */
class PipePortalServiceLinksTest {
    private static final String SCENE_ID = "scene-1";
    private static final String OTHER_SCENE_ID = "scene-2";

    private Nitrite nitrite;
    private PipePortalService service;

    @BeforeEach
    void prepare() {
        nitrite = Nitrite.builder()
            .loadModule(module(new NitriteJacksonMapper()))
            .openOrCreate();
        service = new PipePortalService(new NitritePipePortalRepository(nitrite.getRepository(PipePortalRecord.class)));
    }

    @AfterEach
    void tearDown() {
        nitrite.close();
    }

    @Test
    @DisplayName("Linking two free portals points each at the other")
    void linkSetsBothSides() {
        // Prepare
        final PipePortal entrance = save(SCENE_ID, portal("a", IN));
        final PipePortal exit = save(OTHER_SCENE_ID, portal("b", OUT));

        // Execute
        service.link(entrance.getId(), exit.getId());

        // Verify
        assertThat(stored("a").getLinksToId()).isEqualTo("b");
        assertThat(stored("b").getLinksToId()).isEqualTo("a");
    }

    @Test
    @DisplayName("Two IN_AND_OUT portals can be linked")
    void twoWayPortalsCanBeLinked() {
        // Prepare
        save(SCENE_ID, portal("a", IN_AND_OUT), portal("b", IN_AND_OUT));

        // Execute
        service.link("a", "b");

        // Verify
        assertThat(stored("a").getLinksToId()).isEqualTo("b");
        assertThat(stored("b").getLinksToId()).isEqualTo("a");
    }

    @Test
    @DisplayName("Two ways in, or two ways out, are refused and nothing is stored")
    void sameDirectionPairsAreRefused() {
        // Prepare
        save(SCENE_ID, portal("in-1", IN), portal("in-2", IN), portal("out-1", OUT), portal("out-2", OUT));

        // Execute & Verify
        assertThatThrownBy(() -> service.link("in-1", "in-2")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.link("out-1", "out-2")).isInstanceOf(IllegalStateException.class);
        assertThat(service.findAll()).noneMatch(PipePortal::isLinked);
    }

    @Test
    @DisplayName("A portal cannot be linked to itself, to a missing portal, or while already linked")
    void invalidLinksAreRefused() {
        // Prepare
        save(SCENE_ID, portal("a", IN), portal("b", OUT), portal("c", OUT));
        service.link("a", "b");

        // Execute & Verify
        assertThatThrownBy(() -> service.link("c", "c")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.link("c", "missing")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.link("a", "c"))
            .as("The entrance is already linked")
            .isInstanceOf(IllegalStateException.class);
        assertThat(stored("c").getLinksToId()).isNull();
        assertThat(stored("a").getLinksToId()).isEqualTo("b");
    }

    @Test
    @DisplayName("Unlinking a portal frees both ends")
    void unlinkFreesBothSides() {
        // Prepare
        save(SCENE_ID, portal("a", IN));
        save(OTHER_SCENE_ID, portal("b", OUT));
        service.link("a", "b");

        // Execute
        service.unlink("b");

        // Verify
        assertThat(stored("a").getLinksToId()).isNull();
        assertThat(stored("b").getLinksToId()).isNull();
    }

    @Test
    @DisplayName("Unlinking a portal whose partner is gone frees it")
    void unlinkClearsADanglingLink() {
        // Prepare
        final PipePortal dangling = portal("a", IN);
        dangling.setLinksToId("gone");
        nitrite.getRepository(PipePortalRecord.class).insert(service.toPipePortalRecord(dangling, SCENE_ID));

        // Execute
        service.unlink("a");

        // Verify
        assertThat(stored("a").getLinksToId()).isNull();
    }

    @Test
    @DisplayName("Saving from a tab loaded before the link was made keeps the link")
    void saveFromAStaleTabKeepsTheLink() {
        // Prepare - the tab loaded its portals while they were still free
        save(SCENE_ID, portal("a", IN));
        save(OTHER_SCENE_ID, portal("b", OUT));
        final List<PipePortal> staleTab = service.findByLevelSceneId(SCENE_ID);
        service.link("a", "b");

        // Execute
        service.replaceAll(SCENE_ID, staleTab);

        // Verify
        assertThat(staleTab.getFirst().getLinksToId()).as("The tab's copy").isNull();
        assertThat(stored("a").getLinksToId()).isEqualTo("b");
        assertThat(stored("b").getLinksToId()).isEqualTo("a");
    }

    @Test
    @DisplayName("A portal placed after the last save is stored free, whatever the tab holds")
    void newPortalIsStoredFree() {
        // Prepare
        final PipePortal placed = portal("new", IN);
        placed.setLinksToId("not-stored-anywhere");

        // Execute
        service.replaceAll(SCENE_ID, List.of(placed));

        // Verify
        assertThat(stored("new").getLinksToId()).isNull();
    }

    @Test
    @DisplayName("Removing a linked portal and saving frees its partner in another scene")
    void removedPortalFreesItsPartner() {
        // Prepare
        save(SCENE_ID, portal("a", IN), portal("kept", IN));
        save(OTHER_SCENE_ID, portal("b", OUT));
        service.link("a", "b");
        final List<PipePortal> tab = new ArrayList<>(service.findByLevelSceneId(SCENE_ID));
        tab.removeIf(portal -> portal.getId().equals("a"));

        // Execute
        service.replaceAll(SCENE_ID, tab);

        // Verify
        assertThat(service.findById("a")).isEmpty();
        assertThat(stored("b").getLinksToId()).isNull();
        assertThat(stored("kept")).isNotNull();
    }

    @Test
    @DisplayName("Deleting a scene's portals frees their partners in other scenes")
    void deletingASceneFreesThePartners() {
        // Prepare
        save(SCENE_ID, portal("a", IN), portal("c", IN_AND_OUT));
        save(OTHER_SCENE_ID, portal("b", OUT), portal("d", IN_AND_OUT), portal("e", OUT), portal("f", IN));
        service.link("a", "b");
        service.link("c", "d");
        service.link("f", "e");

        // Execute
        service.deleteByLevelSceneId(SCENE_ID);

        // Verify
        assertThat(service.findByLevelSceneId(SCENE_ID)).isEmpty();
        assertThat(stored("b").getLinksToId()).isNull();
        assertThat(stored("d").getLinksToId()).isNull();
        assertThat(stored("e").getLinksToId()).as("A pair outside the deleted scene").isEqualTo("f");
    }

    private PipePortal save(final String levelSceneId, final PipePortal... portals) {
        final List<PipePortal> current = new ArrayList<>(service.findByLevelSceneId(levelSceneId));
        current.addAll(List.of(portals));
        service.replaceAll(levelSceneId, current);
        return portals[0];
    }

    private PipePortal stored(final String id) {
        return service.findById(id).orElseThrow();
    }

    private static PipePortal portal(final String id, final PipePortalType type) {
        return PipePortal.builder()
            .id(id)
            .type(type)
            .virtualObjectType(PIPE_PORTAL_HORIZONTAL)
            .row(4)
            .column(2)
            .build();
    }
}
