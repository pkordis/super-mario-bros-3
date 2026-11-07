package house.x1337.app.smb3.ui.editor.level.virtualobject.linkage;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Pipe Portal Linkage window's behaviour, with the services and the tab system mocked. The stored portals are
 * kept in {@link #stored}, which the mocked service returns on every {@code findAll}.
 */
class PipePortalLinkageModelTest {
    private static final String WORLD_1 = "scene-1";
    private static final String BONUS_ROOM = "scene-2";

    private final List<PipePortal> stored = new ArrayList<>();
    private final Map<String, String> titles = new HashMap<>();

    private PipePortalService pipePortalService;
    private LevelSceneService levelSceneService;
    private LevelSceneEditorTabSystem tabSystem;
    private PipePortalLinkageModel model;

    @BeforeEach
    void prepare() {
        pipePortalService = mock(PipePortalService.class);
        levelSceneService = mock(LevelSceneService.class);
        tabSystem = mock(LevelSceneEditorTabSystem.class);
        when(pipePortalService.findAll())
            .thenAnswer(invocation -> stored.stream().map(PipePortalLinkageModelTest::copy).toList());
        when(levelSceneService.findTitlesById()).thenAnswer(invocation -> new HashMap<>(titles));
        when(tabSystem.getEditorTabs()).thenReturn(List.of());
        titles.put(WORLD_1, "World 1-1");
        titles.put(BONUS_ROOM, "Bonus room");
        model = new PipePortalLinkageModel(pipePortalService, levelSceneService, tabSystem);
    }

    // -------------------------------------------------------------------------
    // Lists
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Free portals are listed under their type, and linked ones are left out")
    void freePortalsAreSplitByType() {
        // Prepare
        store(portal("in-1", IN, WORLD_1, 1, 1));
        store(portal("out-1", OUT, WORLD_1, 2, 1));
        store(portal("io-1", IN_AND_OUT, WORLD_1, 3, 1));
        store(linked(portal("in-2", IN, WORLD_1, 4, 1), "out-2"));
        store(linked(portal("out-2", OUT, BONUS_ROOM, 5, 1), "in-2"));

        // Execute
        model.reload();

        // Verify
        assertThat(ids(model.getFreePortals(IN))).containsExactly("in-1");
        assertThat(ids(model.getFreePortals(OUT))).containsExactly("out-1");
        assertThat(ids(model.getFreePortals(IN_AND_OUT))).containsExactly("io-1");
    }

    @Test
    @DisplayName("Free portals are sorted by scene title, then column, then row")
    void freePortalsAreSorted() {
        // Prepare
        store(portal("world-c3-r5", IN, WORLD_1, 3, 5));
        store(portal("world-c3-r2", IN, WORLD_1, 3, 2));
        store(portal("world-c1-r9", IN, WORLD_1, 1, 9));
        store(portal("bonus", IN, BONUS_ROOM, 9, 9));

        // Execute
        model.reload();

        // Verify
        assertThat(ids(model.getFreePortals(IN))).containsExactly("bonus", "world-c1-r9", "world-c3-r2", "world-c3-r5");
    }

    @Test
    @DisplayName("Each linked pair is listed once, with its way in first")
    void eachPairIsListedOnce() {
        // Prepare
        store(linked(portal("z-in", IN, WORLD_1, 1, 1), "a-out"));
        store(linked(portal("a-out", OUT, BONUS_ROOM, 1, 1), "z-in"));
        store(linked(portal("io-1", IN_AND_OUT, WORLD_1, 2, 1), "io-2"));
        store(linked(portal("io-2", IN_AND_OUT, BONUS_ROOM, 2, 1), "io-1"));

        // Execute
        model.reload();
        final List<PipePortalLinkagePair> pairs = model.getLinkedPairs();

        // Verify
        assertThat(pairs).hasSize(2);
        assertThat(pairs)
            .extracting(pair -> pair.entrance().getId() + "->" + pair.exit().getId())
            .containsExactlyInAnyOrder("z-in->a-out", "io-1->io-2");
    }

    @Test
    @DisplayName("A portal linked to one that is gone is listed as a pair with a missing exit")
    void danglingLinkIsListedWithAMissingExit() {
        // Prepare
        store(linked(portal("in-1", IN, WORLD_1, 1, 1), "gone"));

        // Execute
        model.reload();
        final List<PipePortalLinkagePair> pairs = model.getLinkedPairs();

        // Verify
        assertThat(pairs).hasSize(1);
        assertThat(pairs.getFirst().entrance().getId()).isEqualTo("in-1");
        assertThat(pairs.getFirst().isDangling()).isTrue();
    }

    @Test
    @DisplayName("The filter matches a scene title or a description, ignoring case, across every list")
    void filterMatchesTitleOrDescription() {
        // Prepare
        store(portal("by-title", IN, BONUS_ROOM, 1, 1));
        final PipePortal byDescription = portal("by-description", OUT, WORLD_1, 2, 1);
        byDescription.setDescription("Down to the BONUS coins");
        store(byDescription);
        store(portal("neither", IN_AND_OUT, WORLD_1, 3, 1));
        store(linked(portal("pair-in", IN, BONUS_ROOM, 4, 1), "pair-out"));
        store(linked(portal("pair-out", OUT, WORLD_1, 5, 1), "pair-in"));
        store(linked(portal("other-in", IN, WORLD_1, 6, 1), "other-out"));
        store(linked(portal("other-out", OUT, WORLD_1, 7, 1), "other-in"));
        model.reload();

        // Execute
        model.setFilter("  bonus ");

        // Verify
        assertThat(ids(model.getFreePortals(IN))).containsExactly("by-title");
        assertThat(ids(model.getFreePortals(OUT))).containsExactly("by-description");
        assertThat(model.getFreePortals(IN_AND_OUT)).isEmpty();
        assertThat(model.getLinkedPairs()).extracting(pair -> pair.entrance().getId()).containsExactly("pair-in");

        // Execute
        model.setFilter("");

        // Verify
        assertThat(model.getFreePortals(IN_AND_OUT)).hasSize(1);
        assertThat(model.getLinkedPairs()).hasSize(2);
    }

    @Test
    @DisplayName("Reloading reads every portal once and every scene title once")
    void reloadMakesTwoQueries() {
        // Prepare
        store(portal("in-1", IN, WORLD_1, 1, 1));
        store(portal("out-1", OUT, BONUS_ROOM, 1, 1));

        // Execute
        model.reload();
        model.getFreePortals(IN);
        model.getFreePortals(OUT);
        model.getLinkedPairs();

        // Verify
        verify(pipePortalService, times(1)).findAll();
        verify(levelSceneService, times(1)).findTitlesById();
    }

    @Test
    @DisplayName("A row reads scene title, description and position, with stand-ins for the missing parts")
    void describeAPortal() {
        // Prepare
        final PipePortal described = portal("in-1", IN, WORLD_1, 12, 7);
        described.setDescription("To the bonus room");
        final PipePortal bare = portal("in-2", IN, "unsaved-or-untitled", 3, 4);
        model.reload();

        // Execute & Verify
        assertThat(model.describe(described)).isEqualTo("World 1-1 · To the bonus room · col 12, row 7");
        assertThat(model.describe(bare)).isEqualTo("(untitled) · — · col 3, row 4");
    }

    // -------------------------------------------------------------------------
    // Slots
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("IN fills the Entrance and OUT fills the Exit, each replacing what was there")
    void inAndOutFillTheirSlots() {
        // Prepare
        final PipePortal firstIn = portal("in-1", IN, WORLD_1, 1, 1);
        final PipePortal secondIn = portal("in-2", IN, WORLD_1, 2, 1);
        final PipePortal out = portal("out-1", OUT, WORLD_1, 3, 1);

        // Execute
        model.choose(firstIn);
        model.choose(out);
        model.choose(secondIn);

        // Verify
        assertThat(model.getEntrance()).isSameAs(secondIn);
        assertThat(model.getExit()).isSameAs(out);
    }

    @Test
    @DisplayName("IN_AND_OUT fills the Entrance, then the Exit, then replaces the Exit")
    void twoWayPortalFillsEntranceThenExit() {
        // Prepare
        final PipePortal first = portal("io-1", IN_AND_OUT, WORLD_1, 1, 1);
        final PipePortal second = portal("io-2", IN_AND_OUT, WORLD_1, 2, 1);
        final PipePortal third = portal("io-3", IN_AND_OUT, WORLD_1, 3, 1);

        // Execute & Verify
        model.choose(first);
        assertThat(model.getEntrance()).isSameAs(first);
        assertThat(model.getExit()).isNull();

        model.choose(second);
        assertThat(model.getEntrance()).isSameAs(first);
        assertThat(model.getExit()).isSameAs(second);

        model.choose(third);
        assertThat(model.getEntrance()).isSameAs(first);
        assertThat(model.getExit()).isSameAs(third);
    }

    @Test
    @DisplayName("A portal already in a slot cannot fill the other one")
    void choosingASlottedPortalChangesNothing() {
        // Prepare
        final PipePortal twoWay = portal("io-1", IN_AND_OUT, WORLD_1, 1, 1);
        model.choose(twoWay);

        // Execute
        model.choose(twoWay);

        // Verify
        assertThat(model.getEntrance()).isSameAs(twoWay);
        assertThat(model.getExit()).isNull();
    }

    @Test
    @DisplayName("Clearing a slot empties only that slot")
    void clearingASlot() {
        // Prepare
        model.choose(portal("in-1", IN, WORLD_1, 1, 1));
        model.choose(portal("out-1", OUT, WORLD_1, 2, 1));

        // Execute
        model.clearEntrance();

        // Verify
        assertThat(model.getEntrance()).isNull();
        assertThat(model.getExit()).isNotNull();

        // Execute
        model.clearExit();

        // Verify
        assertThat(model.getExit()).isNull();
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
        "IN, OUT",
        "IN, IN_AND_OUT",
        "IN_AND_OUT, OUT",
        "IN_AND_OUT, IN_AND_OUT"
    })
    @DisplayName("Link is enabled for every pair the slots can hold")
    void linkEnabledForEveryAllowedPair(final PipePortalType first, final PipePortalType second) {
        // Execute
        model.choose(portal("first", first, WORLD_1, 1, 1));
        model.choose(portal("second", second, WORLD_1, 2, 1));

        // Verify
        assertThat(model.isLinkEnabled()).isTrue();
    }

    @Test
    @DisplayName("Link is disabled while a slot is empty")
    void linkDisabledWithAnEmptySlot() {
        // Execute & Verify
        assertThat(model.isLinkEnabled()).isFalse();
        model.choose(portal("in-1", IN, WORLD_1, 1, 1));
        assertThat(model.isLinkEnabled()).isFalse();
        model.clearEntrance();
        model.choose(portal("out-1", OUT, WORLD_1, 1, 1));
        assertThat(model.isLinkEnabled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Linking and unlinking
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Linking stores (entrance, exit), empties the slots, reloads and updates the open tabs")
    void linkStoresPatchesAndReloads() {
        // Prepare
        final PipePortal entrance = store(portal("in-1", IN, WORLD_1, 1, 1));
        final PipePortal exit = store(portal("out-1", OUT, BONUS_ROOM, 1, 1));
        final PipePortal entranceInTab = copy(entrance);
        final PipePortal unrelatedInTab = portal("other", IN, WORLD_1, 9, 9);
        final PipePortal exitInTab = copy(exit);
        openTabs(List.of(entranceInTab, unrelatedInTab), List.of(exitInTab));
        linkInStoreWhenServiceLinks();
        model.reload();
        model.choose(model.getFreePortals(IN).getFirst());
        model.choose(model.getFreePortals(OUT).getFirst());

        // Execute
        model.link();

        // Verify
        verify(pipePortalService).link("in-1", "out-1");
        assertThat(model.getEntrance()).isNull();
        assertThat(model.getExit()).isNull();
        assertThat(model.getFreePortals(IN)).isEmpty();
        assertThat(model.getFreePortals(OUT)).isEmpty();
        assertThat(model.getLinkedPairs()).hasSize(1);
        assertThat(entranceInTab.getLinksToId()).isEqualTo("out-1");
        assertThat(exitInTab.getLinksToId()).isEqualTo("in-1");
        assertThat(unrelatedInTab.getLinksToId()).isNull();
    }

    @Test
    @DisplayName("A refused link reaches the caller and changes nothing")
    void refusedLinkPropagates() {
        // Prepare
        store(portal("in-1", IN, WORLD_1, 1, 1));
        store(portal("out-1", OUT, BONUS_ROOM, 1, 1));
        doThrow(new IllegalStateException("Linked meanwhile")).when(pipePortalService).link(anyString(), anyString());
        model.reload();
        model.choose(model.getFreePortals(IN).getFirst());
        model.choose(model.getFreePortals(OUT).getFirst());

        // Execute & Verify
        assertThatThrownBy(() -> model.link()).isInstanceOf(IllegalStateException.class);
        assertThat(model.getEntrance()).isNotNull();
        assertThat(model.getExit()).isNotNull();
    }

    @Test
    @DisplayName("Reloading empties a slot whose portal was linked meanwhile")
    void reloadDropsAPortalLinkedMeanwhile() {
        // Prepare
        final PipePortal entrance = store(portal("in-1", IN, WORLD_1, 1, 1));
        model.reload();
        model.choose(model.getFreePortals(IN).getFirst());
        entrance.setLinksToId("elsewhere");

        // Execute
        model.reload();

        // Verify
        assertThat(model.getEntrance()).isNull();
    }

    @Test
    @DisplayName("Link does nothing while it is disabled")
    void linkWhileDisabledDoesNothing() {
        // Prepare
        model.choose(portal("in-1", IN, WORLD_1, 1, 1));

        // Execute
        model.link();

        // Verify
        verify(pipePortalService, never()).link(anyString(), anyString());
    }

    @Test
    @DisplayName("Unlinking frees both ends, reloads and updates the open tabs")
    void unlinkFreesPatchesAndReloads() {
        // Prepare
        store(linked(portal("in-1", IN, WORLD_1, 1, 1), "out-1"));
        store(linked(portal("out-1", OUT, BONUS_ROOM, 1, 1), "in-1"));
        final PipePortal entranceInTab = linked(portal("in-1", IN, WORLD_1, 1, 1), "out-1");
        final PipePortal exitInTab = linked(portal("out-1", OUT, BONUS_ROOM, 1, 1), "in-1");
        openTabs(List.of(entranceInTab), List.of(exitInTab));
        unlinkInStoreWhenServiceUnlinks();
        model.reload();
        final PipePortalLinkagePair pair = model.getLinkedPairs().getFirst();

        // Execute
        model.unlink(pair);

        // Verify
        verify(pipePortalService).unlink("in-1");
        assertThat(model.getLinkedPairs()).isEmpty();
        assertThat(ids(model.getFreePortals(IN))).containsExactly("in-1");
        assertThat(ids(model.getFreePortals(OUT))).containsExactly("out-1");
        assertThat(entranceInTab.getLinksToId()).isNull();
        assertThat(exitInTab.getLinksToId()).isNull();
    }

    @Test
    @DisplayName("Unlinking a dangling pair frees the portal that is left")
    void unlinkADanglingPair() {
        // Prepare
        store(linked(portal("in-1", IN, WORLD_1, 1, 1), "gone"));
        unlinkInStoreWhenServiceUnlinks();
        model.reload();

        // Execute
        model.unlink(model.getLinkedPairs().getFirst());

        // Verify
        verify(pipePortalService).unlink("in-1");
        assertThat(ids(model.getFreePortals(IN))).containsExactly("in-1");
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private PipePortal store(final PipePortal portal) {
        stored.add(portal);
        return portal;
    }

    private void openTabs(final List<PipePortal> firstTab, final List<PipePortal> secondTab) {
        final LevelSceneEditorTab first = mock(LevelSceneEditorTab.class);
        when(first.getPipePortals()).thenReturn(firstTab);
        final LevelSceneEditorTab second = mock(LevelSceneEditorTab.class);
        when(second.getPipePortals()).thenReturn(secondTab);
        when(tabSystem.getEditorTabs()).thenReturn(List.of(first, second));
    }

    /** Makes the mocked service's link change the stored portals, as the real one changes the database. */
    private void linkInStoreWhenServiceLinks() {
        doAnswer(invocation -> {
            final String firstId = invocation.getArgument(0);
            final String secondId = invocation.getArgument(1);
            storedById(firstId).setLinksToId(secondId);
            storedById(secondId).setLinksToId(firstId);
            return null;
        }).when(pipePortalService).link(anyString(), anyString());
    }

    /** Makes the mocked service's unlink change the stored portals, as the real one changes the database. */
    private void unlinkInStoreWhenServiceUnlinks() {
        doAnswer(invocation -> {
            final PipePortal portal = storedById(invocation.getArgument(0));
            stored.stream()
                .filter(other -> other.getId().equals(portal.getLinksToId()))
                .forEach(other -> other.setLinksToId(null));
            portal.setLinksToId(null);
            return null;
        }).when(pipePortalService).unlink(anyString());
    }

    private PipePortal storedById(final String id) {
        return stored.stream().filter(portal -> portal.getId().equals(id)).findFirst().orElseThrow();
    }

    private static PipePortal portal(
        final String id,
        final PipePortalType type,
        final String levelSceneId,
        final int column,
        final int row
    ) {
        return PipePortal.builder()
            .id(id)
            .type(type)
            .virtualObjectType(PIPE_PORTAL_HORIZONTAL)
            .levelSceneId(levelSceneId)
            .column(column)
            .row(row)
            .build();
    }

    private static PipePortal linked(final PipePortal portal, final String partnerId) {
        portal.setLinksToId(partnerId);
        return portal;
    }

    /** What a fresh read returns: an equal but separate object, as the service converts each record anew. */
    private static PipePortal copy(final PipePortal portal) {
        return PipePortal.builder()
            .id(portal.getId())
            .type(portal.getType())
            .description(portal.getDescription())
            .levelSceneId(portal.getLevelSceneId())
            .linksToId(portal.getLinksToId())
            .virtualObjectType(portal.getVirtualObjectType())
            .column(portal.getColumn())
            .row(portal.getRow())
            .build();
    }

    private static List<String> ids(final List<PipePortal> portals) {
        return portals.stream().map(PipePortal::getId).toList();
    }
}
