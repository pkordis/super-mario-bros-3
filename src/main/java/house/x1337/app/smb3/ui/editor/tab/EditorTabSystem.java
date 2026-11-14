package house.x1337.app.smb3.ui.editor.tab;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTabSystem;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editor's tabs, whatever kind of editor each one holds. Listeners added with {@link #addChangeListener} hear
 * about every change of the active tab, including the last tab closing.
 */
@Singleton
public class EditorTabSystem extends BaseEditorTabSystem {
    public void addTab(final BaseEditorTab tab) {
        addTab(tab.resolveTitle(), tab);
        final int index = indexOfComponent(tab);
        installCloseButton(index);
        setSelectedIndex(index);
    }

    public void removeTab(final BaseEditorTab tab) {
        final int index = indexOfComponent(tab);
        if (index >= 0) {
            removeTabAt(index);
        }
    }

    /**
     * Removing the active tab always notifies the change listeners, even when the next tab slides into the same
     * index, so nothing keeps reflecting a tab that is gone.
     */
    @Override
    public void removeTabAt(final int index) {
        final boolean wasActive = index == getSelectedIndex();
        super.removeTabAt(index);
        if (wasActive) {
            fireStateChanged();
        }
    }

    /**
     * Makes {@code tab} the active one.
     *
     * @return {@code false} when the tab is not open
     */
    public boolean select(final BaseEditorTab tab) {
        final int index = indexOfComponent(tab);
        if (index < 0) {
            return false;
        }
        setSelectedIndex(index);
        return true;
    }

    public Optional<BaseEditorTab> getActiveTab() {
        return getActiveTab(BaseEditorTab.class);
    }

    public <T extends BaseEditorTab> Optional<T> getActiveTab(final Class<T> type) {
        final Component selected = getSelectedComponent();
        return type.isInstance(selected) ? Optional.of(type.cast(selected)) : Optional.empty();
    }

    public <T extends BaseEditorTab> List<T> getTabs(final Class<T> type) {
        final List<T> tabs = new ArrayList<>();
        for (int index = 0; index < getTabCount(); index++) {
            final Component component = getComponentAt(index);
            if (type.isInstance(component)) {
                tabs.add(type.cast(component));
            }
        }
        return tabs;
    }

    public void updateTitle(final BaseEditorTab tab) {
        final int index = indexOfComponent(tab);
        if (index < 0) {
            return;
        }
        final String title = tab.resolveTitle();
        setTitleAt(index, title);
        if (getTabComponentAt(index) instanceof final JPanel tabPanel) {
            for (final Component child : tabPanel.getComponents()) {
                if (child instanceof final JLabel label) {
                    label.setText(title);
                    break;
                }
            }
        }
    }
}
