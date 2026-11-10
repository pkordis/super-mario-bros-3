package house.x1337.app.smb3.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link MusicTrack} and {@link SoundEffect} constants and the {@code .ogg} files under {@code sound/} match one
 * to one, each in its own folder.
 */
class SoundAssetsTest {
    private static final String MUSIC_FOLDER = "sound/music";
    private static final String EFFECTS_FOLDER = "sound/effects";

    @Test
    @DisplayName("Every music track points at an existing file in the music folder, and every file there has a track")
    void musicTracksMatchTheMusicFolder() throws IOException, URISyntaxException {
        // Prepare
        final Set<String> paths = Arrays.stream(MusicTrack.values())
            .map(MusicTrack::getPath)
            .collect(Collectors.toSet());

        // Execute
        final Set<String> files = oggFilesIn(MUSIC_FOLDER);

        // Verify
        assertThat(paths).as("One track per constant").hasSize(MusicTrack.values().length);
        assertThat(paths).as("The tracks and the music files").isEqualTo(files);
        assertThat(files).as("Music files").hasSize(32);
    }

    @Test
    @DisplayName("Every sound effect points at an existing file in the effects folder, and every file there has one")
    void soundEffectsMatchTheEffectsFolder() throws IOException, URISyntaxException {
        // Prepare
        final Set<String> paths = Arrays.stream(SoundEffect.values())
            .map(SoundEffect::getPath)
            .collect(Collectors.toSet());

        // Execute
        final Set<String> files = oggFilesIn(EFFECTS_FOLDER);

        // Verify
        assertThat(paths).as("One file per constant").hasSize(SoundEffect.values().length);
        assertThat(paths).as("The effects and the effect files").isEqualTo(files);
        assertThat(files).as("Effect files").hasSize(28);
    }

    @Test
    @DisplayName("Every sound path resolves on the classpath")
    void everySoundPathResolvesOnTheClasspath() {
        // Prepare
        final ClassLoader classLoader = getClass().getClassLoader();
        final Stream<String> paths = Stream.concat(
            Arrays.stream(MusicTrack.values()).map(MusicTrack::getPath),
            Arrays.stream(SoundEffect.values()).map(SoundEffect::getPath)
        );

        // Execute & Verify
        paths.forEach(path -> assertThat(classLoader.getResource(path)).as("Resource %s", path).isNotNull());
    }

    private Set<String> oggFilesIn(final String folder) throws IOException, URISyntaxException {
        final URL url = requireNonNull(getClass().getClassLoader().getResource(folder), folder);
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            return files
                .map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(".ogg"))
                .map(name -> folder + "/" + name)
                .collect(Collectors.toSet());
        }
    }
}
