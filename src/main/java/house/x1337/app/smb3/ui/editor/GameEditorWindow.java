package house.x1337.app.smb3.ui.editor;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.app.ApplicationTerminator;
import house.x1337.app.smb3.ui.editor.menu.EditorMenuBar;
import house.x1337.app.smb3.ui.editor.pane.EditorContentPane;
import house.x1337.app.smb3.ui.editor.toolbar.EditorToolBar;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;

@Singleton
@RequiredArgsConstructor
public final class GameEditorWindow extends JFrame {
    private final EditorContentPane contentPane;
    private final EditorMenuBar menuBar;
    private final EditorToolBar toolBar;
    private final ApplicationTerminator applicationTerminator;
    private final List<EditorStartup> editorStartups;

    @PostConstruct
    void init() {
        addWindowListener(applicationTerminator.getWindowListener());
        setTitle("Game Editor - Super Mario Bros 3");
        setJMenuBar(menuBar);
        final JPanel content = new JPanel(new BorderLayout());
        content.add(toolBar, NORTH);
        content.add(contentPane, CENTER);
        setContentPane(content);
        setMinimumSize(new Dimension(900, 600));
        pack();
        setExtendedState(MAXIMIZED_BOTH);

        editorStartups.forEach(EditorStartup::onEditorStart);
    }
}
