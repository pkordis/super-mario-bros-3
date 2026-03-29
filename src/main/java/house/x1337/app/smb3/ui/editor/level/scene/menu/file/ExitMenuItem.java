package house.x1337.app.smb3.ui.editor.level.scene.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.app.ApplicationTerminator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;

@Singleton
@RequiredArgsConstructor
public class ExitMenuItem extends JMenuItem {
    private final ApplicationTerminator applicationTerminator;

    @PostConstruct
    void init() {
        setText("Exit");
        addActionListener(e -> applicationTerminator.terminate());
    }
}
