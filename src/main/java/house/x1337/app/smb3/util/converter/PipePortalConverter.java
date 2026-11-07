package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.game.level.pipe.PipePortalLinkRulesAware;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.repository.PipePortalRecord;

/**
 * Converts a pipe portal between its domain shape ({@link PipePortal}) and the record it is stored as
 * ({@link PipePortalRecord}). The pipe portal counterpart of {@link EnemyConverter}, implemented by
 * {@code PipePortalService}.
 */
public interface PipePortalConverter extends PipePortalLinkRulesAware {
    default PipePortal toPipePortal(final PipePortalRecord record) {
        return PipePortal.builder()
            .id(record.getId())
            .description(record.getDescription())
            .type(record.getType())
            .virtualObjectType(record.getVirtualObjectType())
            .levelSceneId(record.getLevelSceneId())
            .row(record.getRow())
            .column(record.getColumn())
            .linksToId(record.getLinksToId())
            .build();
    }

    /**
     * Flattens a portal into the record it is stored as.
     *
     * @param portal       the portal to store
     * @param levelSceneId the id of the scene being saved. It is written in place of the portal's own
     *                     {@code levelSceneId}, which is still {@code null} for a portal placed before the scene was
     *                     first saved.
     * @return the record
     */
    default PipePortalRecord toPipePortalRecord(final PipePortal portal, final String levelSceneId) {
        return PipePortalRecord.builder()
            .id(portal.getId())
            .description(portal.getDescription())
            .type(portal.getType())
            .virtualObjectType(portal.getVirtualObjectType())
            .levelSceneId(levelSceneId)
            .row(portal.getRow())
            .column(portal.getColumn())
            .linksToId(portal.getLinksToId())
            .build();
    }
}
