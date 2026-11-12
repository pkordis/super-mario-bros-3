package house.x1337.app.smb3.service;

import house.x1337.app.smb3.model.settings.GameSettings;
import house.x1337.app.smb3.repository.GameSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.TimerPace.STANDARD;
import static house.x1337.app.smb3.enumeration.TimerPace.REAL_TIME;
import static house.x1337.app.smb3.service.GameSettingsService.TIMER_PACE_FIELD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The settings in effect: the defaults until loaded, the saved record field by field after, each missing or unreadable
 * field falling back to its default, and a save that writes every setting and puts it into effect.
 */
class GameSettingsServiceTest {
    private final GameSettingsRepository repository = mock(GameSettingsRepository.class);
    private final GameSettingsService service = new GameSettingsService(repository);

    @Test
    @DisplayName("Before anything is loaded the settings are the defaults")
    void theDefaultsApplyBeforeLoading() {
        // Execute & Verify
        assertThat(service.getSettings().getTimerPace()).isEqualTo(REAL_TIME);
    }

    @Test
    @DisplayName("With no saved record every setting is its default")
    void noRecordMeansTheDefaults() {
        // Prepare
        when(repository.load()).thenReturn(Optional.empty());

        // Execute
        service.load();

        // Verify
        assertThat(service.getSettings()).isEqualTo(new GameSettings());
    }

    @Test
    @DisplayName("A saved value overrides its default")
    void aSavedValueOverridesTheDefault() {
        // Prepare
        when(repository.load()).thenReturn(Optional.of(Map.of(TIMER_PACE_FIELD, "STANDARD")));

        // Execute
        service.load();

        // Verify
        assertThat(service.getSettings().getTimerPace()).isEqualTo(STANDARD);
    }

    @Test
    @DisplayName("A record without the field leaves that setting at its default")
    void aMissingFieldFallsBackToItsDefault() {
        // Prepare
        when(repository.load()).thenReturn(Optional.of(Map.of("somethingElse", "STANDARD")));

        // Execute
        service.load();

        // Verify
        assertThat(service.getSettings().getTimerPace()).isEqualTo(REAL_TIME);
    }

    @Test
    @DisplayName("An unknown name or a value of the wrong type leaves that setting at its default")
    void anUnreadableValueFallsBackToItsDefault() {
        // Execute & Verify
        when(repository.load()).thenReturn(Optional.of(Map.of(TIMER_PACE_FIELD, "SLOW_MOTION")));
        service.load();
        assertThat(service.getSettings().getTimerPace()).as("Unknown name").isEqualTo(REAL_TIME);

        when(repository.load()).thenReturn(Optional.of(Map.of(TIMER_PACE_FIELD, 41)));
        service.load();
        assertThat(service.getSettings().getTimerPace()).as("A number").isEqualTo(REAL_TIME);
    }

    @Test
    @DisplayName("Saving writes every setting by name and puts the settings into effect")
    void savingWritesEverySettingAndAppliesIt() {
        // Prepare
        final GameSettings settings = service.getSettings();
        settings.setTimerPace(STANDARD);

        // Execute
        service.save(settings);

        // Verify
        verify(repository).save(Map.of(TIMER_PACE_FIELD, "STANDARD"));
        assertThat(service.getSettings().getTimerPace()).isEqualTo(STANDARD);
    }

    @Test
    @DisplayName("Saving the defaults still writes them, so the record holds the whole state")
    void savingTheDefaultsWritesThem() {
        // Execute
        service.save(new GameSettings());

        // Verify
        verify(repository).save(Map.of(TIMER_PACE_FIELD, "REAL_TIME"));
    }

    @Test
    @DisplayName("Changing the settings a caller was given does not change the settings in effect")
    void aCallersCopyIsItsOwn() {
        // Prepare
        final GameSettings copy = service.getSettings();

        // Execute
        copy.setTimerPace(STANDARD);

        // Verify
        assertThat(service.getSettings().getTimerPace()).isEqualTo(REAL_TIME);
    }
}
