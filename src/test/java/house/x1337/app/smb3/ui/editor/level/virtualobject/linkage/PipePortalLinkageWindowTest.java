package house.x1337.app.smb3.ui.editor.level.virtualobject.linkage;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JList;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds the real window over a model with mocked services, to check it lays out and shows what the model holds.
 * Skipped where there is no display, since a {@code JDialog} cannot be created headless.
 */
class PipePortalLinkageWindowTest {
    private PipePortalLinkageModel model;

    @BeforeEach
    void prepare() {
        final PipePortalService pipePortalService = mock(PipePortalService.class);
        final LevelSceneService levelSceneService = mock(LevelSceneService.class);
        final LevelSceneEditorTabSystem tabSystem = mock(LevelSceneEditorTabSystem.class);
        when(pipePortalService.findAll()).thenReturn(List.of(
            portal("in-1", IN, null),
            portal("out-1", OUT, null),
            portal("in-2", IN, "out-2"),
            portal("out-2", OUT, "in-2")
        ));
        when(levelSceneService.findTitlesById()).thenReturn(Map.of("scene-1", "World 1-1"));
        when(tabSystem.getEditorTabs()).thenReturn(List.of());
        model = new PipePortalLinkageModel(pipePortalService, levelSceneService, tabSystem);
    }

    @Test
    @DisplayName("The window lists the free portals by type and the linked pairs, with Link and Unlink disabled")
    void opensListingThePortals() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "A JDialog needs a display");

        // Execute
        final PipePortalLinkageWindow window = new PipePortalLinkageWindow(null, model);

        try {
            // Verify
            final List<JList<?>> lists = componentsOf(window.getContentPane(), JList.class);
            assertThat(lists).as("In, Out, In and Out, Linked pairs").hasSize(4);
            assertThat(lists).extracting(list -> list.getModel().getSize()).containsExactly(1, 1, 0, 1);
            final List<JButton> buttons = componentsOf(window.getContentPane(), JButton.class);
            assertThat(button(buttons, "Link").isEnabled()).isFalse();
            assertThat(button(buttons, "Unlink").isEnabled()).isFalse();
        } finally {
            window.dispose();
        }
    }

    private static PipePortal portal(final String id, final PipePortalType type, final String linksToId) {
        return PipePortal.builder()
            .id(id)
            .type(type)
            .virtualObjectType(PIPE_PORTAL_HORIZONTAL)
            .levelSceneId("scene-1")
            .linksToId(linksToId)
            .build();
    }

    private static JButton button(final List<JButton> buttons, final String text) {
        return buttons.stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> componentsOf(final Container container, final Class<?> type) {
        final List<T> found = new ArrayList<>();
        for (final Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                found.add((T) component);
            }
            if (component instanceof final Container child) {
                found.addAll(componentsOf(child, type));
            }
        }
        return found;
    }
}
