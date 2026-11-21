package house.x1337.app.smb3.ui.editor.level.service;

import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The selection tells its listeners whenever it changes, which is what keeps the grid's preview current. */
class PaletteSelectionServiceTest {
    private final PaletteSelectionService paletteSelectionService = new PaletteSelectionService();
    private final AtomicInteger changes = new AtomicInteger();

    @Test
    @DisplayName("Selecting and clearing each tell the listeners once, and clearing nothing tells them nothing")
    void listenersHearEveryChange() {
        // Prepare
        final LayerGatedToggleButton first = mock(LayerGatedToggleButton.class);
        final LayerGatedToggleButton second = mock(LayerGatedToggleButton.class);
        paletteSelectionService.addSelectionListener(changes::incrementAndGet);

        // Execute & Verify
        paletteSelectionService.select(first);
        assertThat(changes).as("Select").hasValue(1);
        paletteSelectionService.select(second);
        assertThat(changes).as("Select another, which releases the first").hasValue(2);
        verify(first).markSelected(false);
        paletteSelectionService.clearSelection();
        assertThat(changes).as("Clear").hasValue(3);
        paletteSelectionService.clearSelection();
        assertThat(changes).as("Clear with nothing selected").hasValue(3);
    }

    @Test
    @DisplayName("A removed listener hears nothing more")
    void removedListenerHearsNothing() {
        // Prepare
        final Runnable listener = changes::incrementAndGet;
        paletteSelectionService.addSelectionListener(listener);

        // Execute
        paletteSelectionService.removeSelectionListener(listener);
        paletteSelectionService.select(mock(LayerGatedToggleButton.class));

        // Verify
        assertThat(changes).hasValue(0);
    }
}
