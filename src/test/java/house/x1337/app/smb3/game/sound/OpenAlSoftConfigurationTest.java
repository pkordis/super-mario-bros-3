package house.x1337.app.smb3.game.sound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static house.x1337.app.smb3.game.sound.OpenAlSoftConfiguration.CONFIG_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpenAlSoftConfiguration} against a mocked process environment.
 */
class OpenAlSoftConfigurationTest {
    private final NativeEnvironment nativeEnvironment = mock(NativeEnvironment.class);
    private final OpenAlSoftConfiguration configuration = new OpenAlSoftConfiguration(nativeEnvironment);

    @Test
    @DisplayName("The variable is pointed at a written config with the small output buffer")
    void theVariablePointsAtTheWrittenConfig() throws IOException {
        // Execute
        configuration.apply();

        // Verify
        final ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(nativeEnvironment).set(eq(CONFIG_VARIABLE), path.capture());
        final String contents = Files.readString(Path.of(path.getValue()));
        assertThat(contents).isEqualTo(configuration.contents());
        assertThat(contents).contains("[general]", "period_size = 480", "periods = 2");
    }

    @Test
    @DisplayName("A config set before the JVM started is left alone")
    void anExistingConfigIsLeftAlone() {
        // Prepare
        when(nativeEnvironment.get(CONFIG_VARIABLE)).thenReturn("C:/mine/alsoft.ini");

        // Execute
        configuration.apply();

        // Verify
        verify(nativeEnvironment, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("The config is applied once per process, however many engines start")
    void theConfigIsAppliedOnce() {
        // Execute
        configuration.apply();
        configuration.apply();

        // Verify
        verify(nativeEnvironment, times(1)).set(eq(CONFIG_VARIABLE), any());
    }

    @Test
    @DisplayName("A refused native call is logged, not thrown: sound plays on OpenAL's defaults")
    void aRefusedNativeCallIsNotThrown() {
        // Prepare
        doThrow(new IllegalCallerException("native access is not enabled"))
            .when(nativeEnvironment).set(anyString(), anyString());

        // Execute & Verify
        assertThatCode(configuration::apply).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The real process environment accepts a variable")
    void theRealEnvironmentAcceptsAVariable() {
        // Prepare
        final NativeEnvironment environment = new NativeEnvironment();

        // Execute & Verify
        assertThatCode(() -> environment.set("SMB3_NATIVE_ENVIRONMENT_TEST", "set from a test"))
            .doesNotThrowAnyException();
    }
}
