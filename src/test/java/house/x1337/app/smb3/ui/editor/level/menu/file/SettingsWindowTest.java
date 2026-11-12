package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.repository.GameSettingsRepository;
import house.x1337.app.smb3.service.GameSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.TimerPace.STANDARD;
import static house.x1337.app.smb3.enumeration.TimerPace.REAL_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Builds the real Settings window over a settings service with a mocked repository: it opens on the settings in
 * effect, Save stores the whole state and closes, Cancel stores nothing. Skipped where there is no display, since a
 * {@code JDialog} cannot be created headless.
 */
class SettingsWindowTest {
    private final GameSettingsRepository repository = mock(GameSettingsRepository.class);
    private final GameSettingsService service = new GameSettingsService(repository);

    @BeforeEach
    void prepare() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "A JDialog needs a display");
    }

    @Test
    @DisplayName("With nothing saved the window opens with the standard timer unticked")
    void opensUntickedByDefault() {
        // Execute
        final SettingsWindow window = new SettingsWindow(null, service);

        // Verify
        assertThat(standardTimerCheckBox(window).isSelected()).isFalse();
        assertThat(window.isModal()).isTrue();
        assertThat(window.getTitle()).isEqualTo("Settings");
        window.dispose();
    }

    @Test
    @DisplayName("With the standard timer saved the window opens with it ticked")
    void opensOnTheSavedSettings() {
        // Prepare
        when(repository.load()).thenReturn(Optional.of(Map.of("timerPace", "STANDARD")));
        service.load();

        // Execute
        final SettingsWindow window = new SettingsWindow(null, service);

        // Verify
        assertThat(standardTimerCheckBox(window).isSelected()).isTrue();
        window.dispose();
    }

    @Test
    @DisplayName("Save stores the whole settings state, puts it into effect and closes the window")
    void saveStoresAndCloses() {
        // Prepare
        final SettingsWindow window = new SettingsWindow(null, service);
        standardTimerCheckBox(window).setSelected(true);

        // Execute
        button(window, "Save").doClick();

        // Verify
        verify(repository).save(Map.of("timerPace", "STANDARD"));
        assertThat(service.getSettings().getTimerPace()).isEqualTo(STANDARD);
        assertThat(window.isDisplayable()).as("Closed").isFalse();
    }

    @Test
    @DisplayName("Cancel stores nothing and leaves the settings in effect as they were")
    void cancelStoresNothing() {
        // Prepare
        final SettingsWindow window = new SettingsWindow(null, service);
        standardTimerCheckBox(window).setSelected(true);

        // Execute
        button(window, "Cancel").doClick();

        // Verify
        verify(repository, never()).save(any());
        assertThat(service.getSettings().getTimerPace()).isEqualTo(REAL_TIME);
        assertThat(window.isDisplayable()).as("Closed").isFalse();
    }

    private JCheckBox standardTimerCheckBox(final SettingsWindow window) {
        final List<JCheckBox> checkBoxes = componentsOf(window.getContentPane(), JCheckBox.class);
        assertThat(checkBoxes).as("The only setting").hasSize(1);
        return checkBoxes.getFirst();
    }

    private AbstractButton button(final SettingsWindow window, final String text) {
        return componentsOf(window.getContentPane(), JButton.class)
            .stream()
            .filter(button -> text.equals(button.getText()))
            .findFirst()
            .orElseThrow();
    }

    private <C extends Component> List<C> componentsOf(final Container container, final Class<C> type) {
        final List<C> found = new ArrayList<>();
        for (final Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                found.add(type.cast(component));
            }
            if (component instanceof final Container child) {
                found.addAll(componentsOf(child, type));
            }
        }
        return found;
    }
}
