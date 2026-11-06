package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.PipePortalRepository;
import house.x1337.app.smb3.util.converter.PipePortalConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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

    public void replaceAll(final String levelSceneId, final List<PipePortal> pipePortals) {
        final List<PipePortalRecord> records = pipePortals == null
            ? List.of()
            : pipePortals.stream().map(portal -> toPipePortalRecord(portal, levelSceneId)).toList();
        pipePortalRepository.deleteByLevelSceneId(levelSceneId);
        pipePortalRepository.insertAll(records);
        log.info("Saved {} pipe portal(s) for scene id={}", records.size(), levelSceneId);
    }

    public void deleteByLevelSceneId(final String levelSceneId) {
        pipePortalRepository.deleteByLevelSceneId(levelSceneId);
        log.info("Deleted the pipe portals of scene id={}", levelSceneId);
    }
}
