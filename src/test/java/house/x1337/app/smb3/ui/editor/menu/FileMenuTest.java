package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.app.ApplicationTerminator;
import house.x1337.app.smb3.service.GameSettingsService;
import house.x1337.app.smb3.ui.editor.level.menu.file.LevelSceneFileMenuContribution;
import house.x1337.app.smb3.ui.editor.level.menu.file.SaveLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.tab.GameEngineTesterTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.menu.file.ExitMenuItem;
import house.x1337.app.smb3.ui.editor.menu.file.SettingsMenuItem;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.JPopupMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The File menu offers Settings... and Exit, each in its own group, below the items of the active tab's editor.
 */
class FileMenuTest {
    private final EditorTabSystem editorTabSystem = mock(EditorTabSystem.class);
    private final SettingsMenuItem settings = new SettingsMenuItem(mock(GameSettingsService.class));
    private final ExitMenuItem exit = new ExitMenuItem(mock(ApplicationTerminator.class));
    private final SaveLevelSceneMenuItem save = new SaveLevelSceneMenuItem(mock(LevelSceneTabs.class));
    private FileMenu fileMenu;
    private ChangeListener activeTabListener;

    @BeforeEach
    void prepare() {
        when(editorTabSystem.getActiveTab()).thenReturn(Optional.empty());
        fileMenu = new FileMenu(
            editorTabSystem,
            List.of(new LevelSceneFileMenuContribution(save)),
            settings,
            exit
        );
        fileMenu.init();
        final ArgumentCaptor<ChangeListener> listener = ArgumentCaptor.forClass(ChangeListener.class);
        verify(editorTabSystem).addChangeListener(listener.capture());
        activeTabListener = listener.getValue();
    }

    @Test
    @DisplayName("With no tab open, File holds only Settings... and Exit")
    void withoutATabOnlySettingsAndExit() {
        // Execute
        final List<Component> items = items();

        // Verify
        assertThat(items).hasSize(3);
        assertThat(items.get(0)).isSameAs(settings);
        assertThat(items.get(1)).as("Separator before Exit").isInstanceOf(JPopupMenu.Separator.class);
        assertThat(items.get(2)).isSameAs(exit);
    }

    @Test
    @DisplayName("While a level scene tab is active, File starts with Save Level Scene in its own group")
    void aLevelSceneTabAddsSave() {
        // Execute
        activate(mock(LevelSceneEditorTab.class));

        // Verify
        final List<Component> items = items();
        assertThat(items).hasSize(5);
        assertThat(items.get(0)).isSameAs(save);
        assertThat(items.get(1)).as("Separator before Settings...").isInstanceOf(JPopupMenu.Separator.class);
        assertThat(items.get(2)).isSameAs(settings);
        assertThat(items.get(3)).as("Separator before Exit").isInstanceOf(JPopupMenu.Separator.class);
        assertThat(items.get(4)).isSameAs(exit);
    }

    @Test
    @DisplayName("Switching to another kind of tab, or closing the last one, hides Save Level Scene again")
    void otherTabsHideSave() {
        // Prepare
        activate(mock(LevelSceneEditorTab.class));

        // Execute & Verify
        activate(mock(GameEngineTesterTab.class));
        assertThat(items()).as("The tester tab is active").doesNotContain(save).hasSize(3);

        activate(mock(LevelSceneEditorTab.class));
        assertThat(items()).as("A level scene tab is active again").contains(save);

        when(editorTabSystem.getActiveTab()).thenReturn(Optional.empty());
        activeTabListener.stateChanged(new ChangeEvent(editorTabSystem));
        assertThat(items()).as("No tab is left").doesNotContain(save).hasSize(3);
    }

    private void activate(final BaseEditorTab tab) {
        when(editorTabSystem.getActiveTab()).thenReturn(Optional.of(tab));
        activeTabListener.stateChanged(new ChangeEvent(editorTabSystem));
    }

    private List<Component> items() {
        return Arrays.asList(fileMenu.getMenuComponents());
    }
}
