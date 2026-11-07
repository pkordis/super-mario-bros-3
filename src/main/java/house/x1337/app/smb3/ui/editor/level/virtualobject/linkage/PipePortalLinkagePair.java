package house.x1337.app.smb3.ui.editor.level.virtualobject.linkage;

import house.x1337.app.smb3.model.game.portal.PipePortal;

public record PipePortalLinkagePair(
    PipePortal entrance,
    PipePortal exit
) {
    public boolean isDangling() {
        return exit == null;
    }
}
