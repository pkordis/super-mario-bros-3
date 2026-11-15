package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.ui.editor.level.tab.GameEngineTesterTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.toolbar.LevelSceneTestRunTarget;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static java.awt.event.KeyEvent.VK_F9;
import static javax.swing.KeyStroke.getKeyStroke;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level &gt; Scene &gt; Test Level Scene and the toolbar's Run Level share one target, and the menu item shows the
 * F9 shortcut.
 */
class TestLevelSceneMenuItemTest {
    private final EditorTabSystem editorTabSystem = mock(EditorTabSystem.class);
    private final LevelSceneTabs levelSceneTabs = new LevelSceneTabs(editorTabSystem, mock(LevelSceneService.class));
    private final LevelSceneTestRunTarget target = spy(new LevelSceneTestRunTarget());

    @BeforeEach
    void prepare() {
        when(editorTabSystem.getActiveTab(LevelSceneEditorTab.class)).thenReturn(Optional.empty());
        doNothing().when(target).run(any());
    }

    @Test
    @DisplayName("The menu item shows F9 as its accelerator")
    void showsTheShortcut() {
        // Prepare
        final TestLevelSceneMenuItem item = new TestLevelSceneMenuItem(levelSceneTabs, target);

        // Execute
        item.init();

        // Verify
        assertThat(item.getText()).isEqualTo("Test Level Scene");
        assertThat(item.getAccelerator()).isEqualTo(getKeyStroke(VK_F9, 0));
    }

    @Test
    @DisplayName("Clicking it runs the active level scene through the same target as the Run button")
    void clickRunsTheSharedTarget() {
        // Prepare
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(editorTabSystem.getActiveTab(LevelSceneEditorTab.class)).thenReturn(Optional.of(tab));
        final TestLevelSceneMenuItem item = new TestLevelSceneMenuItem(levelSceneTabs, target);
        item.init();
        item.setEnabled(true);

        // Execute
        item.doClick();

        // Verify
        verify(target, times(1)).run(tab);
    }

    @Test
    @DisplayName("The target handles level scene tabs only, under the label Run Level")
    void targetHandlesLevelSceneTabsOnly() {
        // Prepare
        final LevelSceneTestRunTarget levelTarget = new LevelSceneTestRunTarget();

        // Execute & Verify
        assertThat(levelTarget.getLabel()).isEqualTo("Run Level");
        assertThat(levelTarget.handles(mock(LevelSceneEditorTab.class))).isTrue();
        assertThat(List.of(mock(GameEngineTesterTab.class), mock(BaseEditorTab.class)))
            .noneMatch(levelTarget::handles);
    }

    @Test
    @DisplayName("Given any other tab the target runs nothing")
    void targetIgnoresOtherTabs() {
        // Prepare
        final GameEngineTesterTab tester = mock(GameEngineTesterTab.class);

        // Execute & Verify - no warning and no tester session, which would need a level scene tab
        assertThatCode(() -> new LevelSceneTestRunTarget().run(tester)).doesNotThrowAnyException();
    }
}
