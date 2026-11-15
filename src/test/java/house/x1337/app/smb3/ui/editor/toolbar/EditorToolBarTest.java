package house.x1337.app.smb3.ui.editor.toolbar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JToolBar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editor toolbar lays its sections out by their order, lowest first, with a separator between each.
 */
class EditorToolBarTest {
    @Test
    @DisplayName("Sections are laid out by their order with a separator between each, and the bar stays put")
    void sectionsInOrderWithSeparators() {
        // Prepare
        final JButton first = new JButton("First");
        final JButton second = new JButton("Second");
        final JButton third = new JButton("Third");
        final EditorToolBar toolBar = new EditorToolBar(List.of(
            section(20, third),
            section(0, first, second)
        ));

        // Execute
        toolBar.init();

        // Verify
        assertThat(toolBar.isFloatable()).isFalse();
        assertThat(toolBar.getComponentCount()).isEqualTo(4);
        assertThat(toolBar.getComponentAtIndex(0)).isSameAs(first);
        assertThat(toolBar.getComponentAtIndex(1)).isSameAs(second);
        assertThat(toolBar.getComponentAtIndex(2)).isInstanceOf(JToolBar.Separator.class);
        assertThat(toolBar.getComponentAtIndex(3)).isSameAs(third);
    }

    @Test
    @DisplayName("Test Run is the first section and holds the Run button")
    void testRunComesFirst() {
        // Prepare
        final RunButton runButton = new RunButton(null, List.of());
        final TestRunSection testRun = new TestRunSection(runButton);

        // Execute & Verify
        assertThat(testRun.getOrder()).isZero();
        assertThat(testRun.getTools()).containsExactly(runButton);
    }

    private EditorToolBarSection section(final int order, final JComponent... tools) {
        return new EditorToolBarSection() {
            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public List<JComponent> getTools() {
                return List.of(tools);
            }
        };
    }
}
