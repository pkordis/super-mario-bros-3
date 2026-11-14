package house.x1337.app.smb3.ui.editor.level.menu.levelobject;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.virtualobject.linkage.PipePortalLinkageWindow;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;

@Singleton
public class PipePortalLinkageMenuItem extends GameEditorWindowMenuItem {
    @PostConstruct
    void init() {
        setText("Pipe Portal Linkage");
        addActionListener(e -> PipePortalLinkageWindow.launch(getParentFrame()).setVisible(true));
    }
}
