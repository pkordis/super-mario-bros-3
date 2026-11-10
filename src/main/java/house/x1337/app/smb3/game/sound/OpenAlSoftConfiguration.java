package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shrinks OpenAL Soft's output buffer, which is most of the delay between playing a sound and hearing it.
 *
 * <p>OpenAL mixes in periods and keeps them queued ahead of the sound card, and a new sound waits behind the
 * queue. Its default here is 20 ms periods with about 60 ms queued; {@value #PERIOD_FRAMES} frames
 * (10 ms at 48 kHz) times {@value #PERIODS} measured 22 ms. If sound ever crackles because the mixer missed a
 * period, raise {@link #PERIODS} to 3 (about 32 ms).
 *
 * <p>OpenAL Soft takes these settings only from a config file, named by the {@value #CONFIG_VARIABLE}
 * environment variable, and reads it once, when the library loads. So {@link #apply()} writes the file to the
 * temp folder and sets the variable in this process's own environment through {@link NativeEnvironment}. It must
 * run before the engine opens its audio device. Nothing outside the application is needed, so this works the same
 * from the IDE, from Maven and from a packaged jar. A {@value #CONFIG_VARIABLE} set before the JVM started is left
 * alone, so a config of one's own still wins.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public final class OpenAlSoftConfiguration {
    static final String CONFIG_VARIABLE = "ALSOFT_CONF";
    static final int PERIOD_FRAMES = 480;
    static final int PERIODS = 2;

    private final NativeEnvironment nativeEnvironment;

    private boolean applied;

    /**
     * Points OpenAL Soft at the game's settings, once per process. A failure is logged and leaves OpenAL on its
     * defaults: sound still plays, only later.
     */
    public void apply() {
        if (applied) {
            return;
        }
        applied = true;
        final String existing = nativeEnvironment.get(CONFIG_VARIABLE);
        if (existing != null) {
            log.info("{} is already set to {}; leaving OpenAL's settings to it", CONFIG_VARIABLE, existing);
            return;
        }
        try {
            final Path config = Files.createTempFile("smb3-openal-", ".ini");
            config.toFile().deleteOnExit();
            Files.writeString(config, contents());
            nativeEnvironment.set(CONFIG_VARIABLE, config.toString());
            log.debug("OpenAL Soft output: {} frames x {} periods, from {}", PERIOD_FRAMES, PERIODS, config);
        } catch (final IOException | RuntimeException e) {
            log.warn("Could not configure OpenAL Soft; sounds keep its default output delay", e);
        }
    }

    String contents() {
        return "# Written by the game at startup; see OpenAlSoftConfiguration.\n"
            + "[general]\n"
            + "period_size = " + PERIOD_FRAMES + "\n"
            + "periods = " + PERIODS + "\n";
    }
}
