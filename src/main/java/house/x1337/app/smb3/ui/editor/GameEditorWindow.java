package house.x1337.app.smb3.ui.editor;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.app.ApplicationTerminator;
import house.x1337.app.smb3.ui.editor.menu.EditorMenuBar;
import house.x1337.app.smb3.ui.editor.pane.EditorContentPane;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.util.List;

@Singleton
@RequiredArgsConstructor
public final class GameEditorWindow extends JFrame {
    private final EditorContentPane contentPane;
    private final EditorMenuBar menuBar;
    private final ApplicationTerminator applicationTerminator;
    private final List<EditorStartup> editorStartups;

    @PostConstruct
    void init() {
        addWindowListener(applicationTerminator.getWindowListener());
        setTitle("Game Editor - Super Mario Bros 3");
        setJMenuBar(menuBar);
        setContentPane(contentPane);
        setMinimumSize(new Dimension(900, 600));
        pack();
        setExtendedState(MAXIMIZED_BOTH);

        editorStartups.forEach(EditorStartup::onEditorStart);
    }
}
