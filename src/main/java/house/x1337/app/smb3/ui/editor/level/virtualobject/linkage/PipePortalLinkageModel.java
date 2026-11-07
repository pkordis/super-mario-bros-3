package house.x1337.app.smb3.ui.editor.level.virtualobject.linkage;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.game.level.pipe.PipePortalLinkRulesAware;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;

/**
 * Everything the Pipe Portal Linkage window does, without Swing: the free portals by type, the linked pairs, the
 * filter, the Entrance and Exit slots, and linking and unlinking.
 *
 * <p>{@link #reload()} reads every portal and every scene title once, then works in memory, so hundreds of portals
 * cost two queries per open or refresh.
 */
@Prototype
@RequiredArgsConstructor
public class PipePortalLinkageModel implements PipePortalLinkRulesAware {
    static final String UNTITLED_SCENE = "(untitled)";
    static final String NO_DESCRIPTION = "—";

    private final PipePortalService pipePortalService;
    private final LevelSceneService levelSceneService;
    private final LevelSceneEditorTabSystem tabSystem;

    private final Map<String, PipePortal> portalsById = new HashMap<>();
    private final Map<String, String> sceneTitles = new HashMap<>();
    private String filter = "";

    @Getter
    private PipePortal entrance;
    @Getter
    private PipePortal exit;

    /**
     * Reads every stored portal and scene title again. A slot whose portal is gone or no longer free is emptied.
     */
    public void reload() {
        portalsById.clear();
        for (final PipePortal portal : pipePortalService.findAll()) {
            portalsById.put(portal.getId(), portal);
        }
        sceneTitles.clear();
        sceneTitles.putAll(levelSceneService.findTitlesById());
        entrance = stillFree(entrance);
        exit = stillFree(exit);
    }

    /**
     * Keeps only the rows whose scene title or description contains {@code text}, ignoring case.
     *
     * @param text the filter, or {@code null} / blank for none
     */
    public void setFilter(final String text) {
        filter = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @param type the portal type to list
     * @return the free portals of that type that pass the filter, by scene title, then column, then row
     */
    public List<PipePortal> getFreePortals(final PipePortalType type) {
        return portalsById.values()
            .stream()
            .filter(portal -> portal.getType() == type && !portal.isLinked())
            .filter(this::matchesFilter)
            .sorted(listOrder())
            .toList();
    }

    /**
     * @return every linked pair once, either end passing the filter, in the order of their entrances
     */
    public List<PipePortalLinkagePair> getLinkedPairs() {
        final List<PipePortalLinkagePair> pairs = new ArrayList<>();
        for (final PipePortal portal : portalsById.values()) {
            if (!portal.isLinked()) {
                continue;
            }
            final PipePortal partner = portalsById.get(portal.getLinksToId());
            if (partner == null || !Objects.equals(partner.getLinksToId(), portal.getId())) {
                pairs.add(new PipePortalLinkagePair(portal, null));
            } else if (portal.getId().compareTo(partner.getId()) < 0) {
                pairs.add(orient(portal, partner));
            }
        }
        return pairs.stream()
            .filter(pair -> matchesFilter(pair.entrance()) || (pair.exit() != null && matchesFilter(pair.exit())))
            .sorted(comparing(PipePortalLinkagePair::entrance, listOrder()))
            .toList();
    }

    /**
     * The double click on a free portal. {@code IN} fills Entrance and {@code OUT} fills Exit, replacing what was
     * there. {@code IN_AND_OUT} fills Entrance when it is empty, otherwise Exit when that is empty, otherwise it
     * replaces Exit. A portal already in either slot is ignored, so one portal never fills both.
     *
     * @param portal the portal chosen
     */
    public void choose(final PipePortal portal) {
        if (portal == null || portal.getType() == null || isInASlot(portal)) {
            return;
        }
        switch (portal.getType()) {
            case IN -> entrance = portal;
            case OUT -> exit = portal;
            case IN_AND_OUT -> {
                if (entrance == null) {
                    entrance = portal;
                } else {
                    exit = portal;
                }
            }
        }
    }

    public void clearEntrance() {
        entrance = null;
    }

    public void clearExit() {
        exit = null;
    }

    /**
     * @return {@code true} when both slots are filled with a pair that can be linked
     */
    public boolean isLinkEnabled() {
        return entrance != null && exit != null && isLinkable(entrance.getType(), exit.getType());
    }

    /**
     * Links the Entrance to the Exit in the database, empties both slots, reloads, and brings the open editor tabs
     * in line.
     *
     * @throws IllegalStateException when the link is refused, e.g. one end was linked meanwhile
     */
    public void link() {
        if (!isLinkEnabled()) {
            return;
        }
        final Set<String> ids = Set.of(entrance.getId(), exit.getId());
        pipePortalService.link(entrance.getId(), exit.getId());
        entrance = null;
        exit = null;
        reload();
        patchOpenTabs(ids);
    }

    /**
     * Frees both ends of a pair (or the one end of a dangling pair) in the database, reloads, and brings the open
     * editor tabs in line.
     *
     * @param pair the pair to unlink
     */
    public void unlink(final PipePortalLinkagePair pair) {
        if (pair == null) {
            return;
        }
        final List<String> ids = new ArrayList<>(List.of(pair.entrance().getId()));
        if (pair.exit() != null) {
            ids.add(pair.exit().getId());
        } else if (pair.entrance().getLinksToId() != null) {
            ids.add(pair.entrance().getLinksToId());
        }
        pipePortalService.unlink(pair.entrance().getId());
        reload();
        patchOpenTabs(ids);
    }

    /**
     * @param portal a listed portal
     * @return its row text: {@code <scene title> · <description, or —> · col <c>, row <r>}
     */
    public String describe(final PipePortal portal) {
        if (portal == null) {
            return "";
        }
        final String description = portal.getDescription() == null || portal.getDescription().isBlank()
            ? NO_DESCRIPTION
            : portal.getDescription();
        return "%s · %s · col %d, row %d".formatted(
            sceneTitleOf(portal),
            description,
            portal.getColumn(),
            portal.getRow()
        );
    }

    /**
     * Sets the {@code linksToId} of the open tabs' copies of these portals to what is now stored, so a tab shows the
     * new link state. Saving a tab is safe even without this, because the save keeps the stored links.
     */
    private void patchOpenTabs(final Collection<String> ids) {
        for (final LevelSceneEditorTab tab : tabSystem.getEditorTabs()) {
            if (tab.getPipePortals() == null) {
                continue;
            }
            for (final PipePortal portal : tab.getPipePortals()) {
                if (ids.contains(portal.getId())) {
                    final PipePortal stored = portalsById.get(portal.getId());
                    portal.setLinksToId(stored == null ? null : stored.getLinksToId());
                }
            }
        }
    }

    private PipePortal stillFree(final PipePortal slot) {
        if (slot == null) {
            return null;
        }
        final PipePortal stored = portalsById.get(slot.getId());
        return stored == null || stored.isLinked() ? null : stored;
    }

    private boolean isInASlot(final PipePortal portal) {
        return (entrance != null && entrance.getId().equals(portal.getId()))
            || (exit != null && exit.getId().equals(portal.getId()));
    }

    /** Puts the way in first: {@code IN}, then {@code IN_AND_OUT}, then {@code OUT}; equal types by id. */
    private static PipePortalLinkagePair orient(final PipePortal first, final PipePortal second) {
        final int byType = Integer.compare(entranceRank(first), entranceRank(second));
        final boolean firstLeads = byType < 0 || byType == 0 && first.getId().compareTo(second.getId()) < 0;
        return firstLeads ? new PipePortalLinkagePair(first, second) : new PipePortalLinkagePair(second, first);
    }

    private static int entranceRank(final PipePortal portal) {
        if (portal.getType() == IN) {
            return 0;
        }
        return portal.getType() == OUT ? 2 : 1;
    }

    private boolean matchesFilter(final PipePortal portal) {
        if (filter.isEmpty()) {
            return true;
        }
        final String title = sceneTitles.get(portal.getLevelSceneId());
        return contains(title, filter) || contains(portal.getDescription(), filter);
    }

    private static boolean contains(final String text, final String lowerCaseFilter) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
    }

    private String sceneTitleOf(final PipePortal portal) {
        final String title = sceneTitles.get(portal.getLevelSceneId());
        return title == null || title.isBlank() ? UNTITLED_SCENE : title;
    }

    private Comparator<PipePortal> listOrder() {
        return comparing(this::sceneTitleOf, CASE_INSENSITIVE_ORDER)
            .thenComparingInt(PipePortal::getColumn)
            .thenComparingInt(PipePortal::getRow);
    }
}
