package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.PipePortalRepository;
import house.x1337.app.smb3.util.converter.PipePortalConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toCollection;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PipePortalService implements PipePortalConverter {
    private final PipePortalRepository pipePortalRepository;

    public List<PipePortal> findByLevelSceneId(final String levelSceneId) {
        return pipePortalRepository.findByLevelSceneId(levelSceneId)
            .stream()
            .map(this::toPipePortal)
            .collect(toCollection(ArrayList::new));
    }

    public Optional<PipePortal> findById(final String id) {
        return pipePortalRepository.findById(id).map(this::toPipePortal);
    }

    public List<PipePortal> findAll() {
        return pipePortalRepository.findAll()
            .stream()
            .map(this::toPipePortal)
            .collect(toCollection(ArrayList::new));
    }

    /**
     * Replaces a scene's stored portals with {@code pipePortals}, keeping the links.
     *
     * <p>The database owns the links, not the editor: a tab loaded before a link was made still holds
     * {@code linksToId == null}, so every portal that is already stored keeps its stored {@code linksToId}, and a new
     * one starts free. A stored portal missing from {@code pipePortals} was removed in the editor, so whichever portal
     * was linked to it is freed.
     *
     * @param levelSceneId the saved scene's id
     * @param pipePortals  the scene's portals as the editor holds them, or {@code null} for none
     */
    public void replaceAll(
        final String levelSceneId,
        final List<PipePortal> pipePortals
    ) {
        final Map<String, String> storedLinks = new HashMap<>();
        for (final PipePortalRecord stored : pipePortalRepository.findByLevelSceneId(levelSceneId)) {
            storedLinks.put(stored.getId(), stored.getLinksToId());
        }
        final List<PipePortalRecord> records = pipePortals == null
            ? List.of()
            : pipePortals.stream().map(portal -> toStoredRecord(portal, levelSceneId, storedLinks)).toList();
        final Set<String> removedIds = new HashSet<>(storedLinks.keySet());
        records.forEach(record -> removedIds.remove(record.getId()));

        pipePortalRepository.deleteByLevelSceneId(levelSceneId);
        pipePortalRepository.insertAll(records);
        pipePortalRepository.clearLinksTo(removedIds);
        log.info(
            "Saved {} pipe portal(s) for scene id={}, freed the partners of {} removed one(s)",
            records.size(),
            levelSceneId,
            removedIds.size()
        );
    }

    /**
     * Deletes a scene's portals and frees every portal, in any scene, that was linked to one of them.
     *
     * @param levelSceneId the deleted scene's id
     */
    public void deleteByLevelSceneId(final String levelSceneId) {
        final List<String> ids = pipePortalRepository.findByLevelSceneId(levelSceneId)
            .stream()
            .map(PipePortalRecord::getId)
            .toList();
        pipePortalRepository.deleteByLevelSceneId(levelSceneId);
        pipePortalRepository.clearLinksTo(ids);
        log.info("Deleted the {} pipe portal(s) of scene id={}", ids.size(), levelSceneId);
    }

    /**
     * Links two free portals to each other, as stored in the database.
     *
     * @param firstId  one end
     * @param secondId the other end
     * @throws IllegalStateException when either portal is missing, both ids are the same, either portal is already
     *                               linked, or their types cannot form a pair
     */
    public void link(final String firstId, final String secondId) {
        if (Objects.equals(firstId, secondId)) {
            throw new IllegalStateException("A pipe portal cannot be linked to itself: " + firstId);
        }
        final PipePortalRecord first = stored(firstId);
        final PipePortalRecord second = stored(secondId);
        if (first.getLinksToId() != null || second.getLinksToId() != null) {
            throw new IllegalStateException(
                "Only free pipe portals can be linked: %s, %s".formatted(firstId, secondId)
            );
        }
        if (!isLinkable(first.getType(), second.getType())) {
            throw new IllegalStateException(
                "Pipe portals of types %s and %s cannot be linked".formatted(first.getType(), second.getType())
            );
        }
        pipePortalRepository.updateLinksToId(firstId, secondId);
        pipePortalRepository.updateLinksToId(secondId, firstId);
        log.info("Linked pipe portals {} and {}", firstId, secondId);
    }

    /**
     * Frees a portal and, when its partner still exists and points back at it, the partner too. A portal whose
     * partner is gone is simply freed.
     *
     * @param id the portal to unlink
     */
    public void unlink(final String id) {
        final Optional<PipePortalRecord> portal = pipePortalRepository.findById(id);
        if (portal.isEmpty() || portal.get().getLinksToId() == null) {
            return;
        }
        final String partnerId = portal.get().getLinksToId();
        pipePortalRepository.updateLinksToId(id, null);
        pipePortalRepository.findById(partnerId)
            .filter(partner -> Objects.equals(partner.getLinksToId(), id))
            .ifPresent(partner -> pipePortalRepository.updateLinksToId(partnerId, null));
        log.info("Unlinked pipe portal {} from {}", id, partnerId);
    }

    private PipePortalRecord toStoredRecord(
        final PipePortal portal,
        final String levelSceneId,
        final Map<String, String> storedLinks
    ) {
        final PipePortalRecord record = toPipePortalRecord(portal, levelSceneId);
        record.setLinksToId(storedLinks.get(portal.getId()));
        return record;
    }

    private PipePortalRecord stored(final String id) {
        return pipePortalRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("No stored pipe portal with id " + id));
    }
}
