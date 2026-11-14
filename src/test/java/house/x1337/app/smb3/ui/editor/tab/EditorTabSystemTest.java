package house.x1337.app.smb3.ui.editor.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * The tab system holds any kind of editor tab: it finds the active one by kind, and closing a tab removes it before
 * the tab cleans up after itself.
 */
class EditorTabSystemTest {
    private final EditorTabSystem editorTabSystem = new EditorTabSystem();
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(EditorTabSystem.class)).thenReturn(editorTabSystem);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A tab that is closed is removed first and then told it was closed")
    void closingRemovesThenNotifies() {
        // Prepare
        final AtomicInteger tabsLeftWhenNotified = new AtomicInteger(-1);
        final BaseEditorTab tab = new TestTab("Tab") {
            @Override
            public void onClose() {
                tabsLeftWhenNotified.set(editorTabSystem.getTabCount());
            }
        };
        editorTabSystem.addTab(tab);

        // Execute
        editorTabSystem.closeTabAt(0);

        // Verify
        assertThat(editorTabSystem.getTabCount()).isZero();
        assertThat(tabsLeftWhenNotified).as("Tabs open while the closed tab cleans up").hasValue(0);
    }

    @Test
    @DisplayName("The active tab is found by its kind, and not found while a tab of another kind is active")
    void activeTabByKind() {
        // Prepare
        final OtherTab other = new OtherTab();
        final TestTab tab = new TestTab("Tab");
        editorTabSystem.addTab(other);
        editorTabSystem.addTab(tab);

        // Execute & Verify
        assertThat(editorTabSystem.getActiveTab()).containsSame(tab);
        assertThat(editorTabSystem.getActiveTab(TestTab.class)).containsSame(tab);
        assertThat(editorTabSystem.getActiveTab(OtherTab.class)).isEmpty();
        assertThat(editorTabSystem.getTabs(TestTab.class)).containsExactly(tab);
    }

    @Test
    @DisplayName("Closing the active tab notifies the listeners even when the next tab takes its place")
    void closingTheActiveTabNotifies() {
        // Prepare
        editorTabSystem.addTab(new TestTab("First"));
        editorTabSystem.addTab(new TestTab("Second"));
        editorTabSystem.setSelectedIndex(0);
        final List<Integer> selectedWhenNotified = new ArrayList<>();
        editorTabSystem.addChangeListener(event -> selectedWhenNotified.add(editorTabSystem.getSelectedIndex()));

        // Execute
        editorTabSystem.closeTabAt(0);

        // Verify
        assertThat(selectedWhenNotified).isNotEmpty().allSatisfy(index -> assertThat(index).isZero());
        assertThat(editorTabSystem.getTitleAt(0)).isEqualTo("Second");
    }

    @Test
    @DisplayName("A tab's title is shown again from its current title")
    void updatesTheTitle() {
        // Prepare
        final TestTab tab = new TestTab("Before");
        editorTabSystem.addTab(tab);
        tab.title = "After";

        // Execute
        tab.updateTabTitle();

        // Verify
        assertThat(editorTabSystem.getTitleAt(0)).isEqualTo("After");
    }

    private static class TestTab extends BaseEditorTab {
        private String title;

        TestTab(final String title) {
            this.title = title;
        }

        @Override
        public String resolveTitle() {
            return title;
        }
    }

    private static final class OtherTab extends BaseEditorTab {
        @Override
        public String resolveTitle() {
            return "Other";
        }
    }
}
