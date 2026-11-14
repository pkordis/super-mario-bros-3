package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.menu.file.ExitMenuItem;
import house.x1337.app.smb3.ui.editor.menu.file.SettingsMenuItem;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.List;
import java.util.Optional;

/**
 * Settings and Exit, below the items the active tab's editor contributes (see {@link FileMenuContribution}).
 *
 * <p>The menu is rebuilt whenever the active tab changes, not only when it opens, so the accelerator of an item
 * that does not apply to the active tab is not bound either.
 */
@Singleton
@RequiredArgsConstructor
public class FileMenu extends JMenu {
    private final EditorTabSystem editorTabSystem;
    private final List<FileMenuContribution> contributions;
    private final SettingsMenuItem settingsMenuItem;
    private final ExitMenuItem exitMenuItem;

    @PostConstruct
    void init() {
        setText("File");
        editorTabSystem.addChangeListener(event -> rebuild());
        rebuild();
    }

    private void rebuild() {
        removeAll();
        final Optional<BaseEditorTab> activeTab = editorTabSystem.getActiveTab();
        final List<JMenuItem> tabItems = contributions.stream()
            .filter(contribution -> activeTab.isPresent() && contribution.appliesTo(activeTab.get()))
            .flatMap(contribution -> contribution.getItems().stream())
            .toList();
        if (!tabItems.isEmpty()) {
            tabItems.forEach(this::add);
            addSeparator();
        }
        add(settingsMenuItem);
        addSeparator();
        add(exitMenuItem);
    }
}
