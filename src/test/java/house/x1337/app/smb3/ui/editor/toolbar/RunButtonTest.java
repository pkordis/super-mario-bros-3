package house.x1337.app.smb3.ui.editor.toolbar;

import house.x1337.app.smb3.ui.editor.level.tab.GameEngineTesterTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.toolbar.LevelSceneTestRunTarget;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.ui.editor.toolbar.RunButton.SHORTCUT;
import static java.awt.event.KeyEvent.VK_F9;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The toolbar's Run button follows the active tab: Run Level while a level scene tab is active, disabled while
 * nothing it can run is, and F9 does what a click does.
 */
class RunButtonTest {
    private final EditorTabSystem editorTabSystem = mock(EditorTabSystem.class);
    private final LevelSceneTestRunTarget levelTarget = spy(new LevelSceneTestRunTarget());
    private RunButton button;

    @BeforeEach
    void prepare() {
        when(editorTabSystem.getActiveTab()).thenReturn(Optional.empty());
        doNothing().when(levelTarget).run(any());
        button = new RunButton(editorTabSystem, List.of(levelTarget));
        button.init();
    }

    @Test
    @DisplayName("With no tab open the button is disabled and reads Run")
    void disabledWithoutATab() {
        // Verify
        assertThat(button.isEnabled()).isFalse();
        assertThat(button.getText()).isEqualTo("Run");
        assertThat(button.getToolTipText()).isEqualTo("Nothing to run in the active tab");
    }

    @Test
    @DisplayName("A level scene tab makes it Run Level, enabled, with F9 in the tooltip")
    void runLevelForALevelSceneTab() {
        // Execute
        activate(mock(LevelSceneEditorTab.class));

        // Verify
        assertThat(button.isEnabled()).isTrue();
        assertThat(button.getText()).isEqualTo("Run Level");
        assertThat(button.getToolTipText()).isEqualTo("Run Level (F9)");
    }

    @Test
    @DisplayName("The level scene tester tab has nothing to run, so the button is disabled")
    void disabledForTheTesterTab() {
        // Execute
        activate(mock(GameEngineTesterTab.class));

        // Verify
        assertThat(button.isEnabled()).isFalse();
        assertThat(button.getText()).isEqualTo("Run");
    }

    @Test
    @DisplayName("Any other kind of tab has nothing to run either")
    void disabledForAnotherKindOfTab() {
        // Execute
        activate(mock(BaseEditorTab.class));

        // Verify
        assertThat(button.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Switching tabs updates the button straight away, both ways")
    void followsTheActiveTab() {
        // Execute & Verify
        activate(mock(LevelSceneEditorTab.class));
        assertThat(button.isEnabled()).isTrue();

        activate(mock(GameEngineTesterTab.class));
        assertThat(button.isEnabled()).isFalse();

        activate(mock(LevelSceneEditorTab.class));
        assertThat(button.isEnabled()).isTrue();

        activate(null);
        assertThat(button.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("A click runs the active level scene through its target")
    void clickRunsTheActiveTab() {
        // Prepare
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        activate(tab);

        // Execute
        button.doClick();

        // Verify
        verify(levelTarget, times(1)).run(tab);
    }

    @Test
    @DisplayName("F9 is bound in the whole window and runs the same target as a click")
    void shortcutRunsTheActiveTab() {
        // Prepare
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        activate(tab);
        final Object actionKey = button.getInputMap(WHEN_IN_FOCUSED_WINDOW).get(getKeyStroke(VK_F9, 0));
        final Action action = button.getActionMap().get(actionKey);

        // Execute
        action.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "F9"));

        // Verify
        assertThat(SHORTCUT).isEqualTo(getKeyStroke(VK_F9, 0));
        verify(levelTarget, times(1)).run(tab);
    }

    @Test
    @DisplayName("With nothing to run, a stray trigger runs nothing")
    void nothingRunsWithoutATarget() {
        // Prepare
        activate(mock(GameEngineTesterTab.class));

        // Execute
        button.runActiveTab();

        // Verify
        verify(levelTarget, never()).run(any());
    }

    @Test
    @DisplayName("The button shows the play icon")
    void showsTheIcon() {
        // Verify
        assertThat(button.getIcon()).isNotNull();
        assertThat(button.getIcon().getIconWidth()).isEqualTo(24);
        assertThat(button.getIcon().getIconHeight()).isEqualTo(24);
    }

    private void activate(final BaseEditorTab tab) {
        when(editorTabSystem.getActiveTab()).thenReturn(Optional.ofNullable(tab));
        final ArgumentCaptor<ChangeListener> listeners = ArgumentCaptor.forClass(ChangeListener.class);
        verify(editorTabSystem, atLeastOnce()).addChangeListener(listeners.capture());
        listeners.getAllValues().forEach(listener -> listener.stateChanged(new ChangeEvent(editorTabSystem)));
    }
}
