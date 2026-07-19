package house.x1337.app.smb3.ui.editor.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.app.ApplicationTerminator;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneEditorMenuBar;
import house.x1337.app.smb3.ui.editor.level.pane.RightPane;
import house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JFrame;
import java.awt.Dimension;

@Singleton
@RequiredArgsConstructor
public final class LevelSceneEditorWindow extends JFrame {
    private final RightPane rightPane;
    private final LevelSceneService levelSceneService;

    private final LevelSceneEditorMenuBar menuBar;
    private final LevelSceneEditorTabSystem editorTabs;
    private final ApplicationTerminator applicationTerminator;

    @PostConstruct
    void init() {
        addWindowListener(applicationTerminator.getWindowListener());
        setTitle("Scene Editor - Super Mario Bros 3");
        setJMenuBar(menuBar);
        setContentPane(rightPane);
        setMinimumSize(new Dimension(900, 600));
        pack();
        setExtendedState(MAXIMIZED_BOTH);

        levelSceneService
            .findLastEdited()
            .ifPresent(scene -> {
                final LevelSceneEditorTab newTab = LevelSceneEditorTabFactory.fromScene(scene);
                editorTabs.addTab(newTab);
                editorTabs.track(newTab);
            });
    }
}
