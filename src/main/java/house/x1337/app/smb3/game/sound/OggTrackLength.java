package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Reads how long an Ogg Vorbis file plays from the file itself: the sample count recorded on its last page, over the
 * sample rate in its identification header. Nothing is decoded. Each file is read once, and the answer is cached.
 *
 * <p>A file that cannot be read, or that holds no such header, counts as 0 seconds long, so a wait on it ends at once
 * rather than never.
 */
@Slf4j
@Singleton
public final class OggTrackLength {
    private static final byte[] PAGE_START = "OggS".getBytes(US_ASCII);
    private static final byte[] VORBIS_IDENTIFICATION = {1, 'v', 'o', 'r', 'b', 'i', 's'};
    private static final int GRANULE_OFFSET = 6;
    private static final int SAMPLE_RATE_OFFSET = 12;

    private final Map<String, Double> secondsByPath = new ConcurrentHashMap<>();

    /**
     * @param path the classpath path of an {@code .ogg} file
     * @return how long the file plays, in seconds
     */
    public double secondsOf(final String path) {
        return secondsByPath.computeIfAbsent(path, this::read);
    }

    private double read(final String path) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                log.warn("{} is not on the classpath: taken as 0 s long", path);
                return 0;
            }
            final ByteBuffer data = ByteBuffer.wrap(stream.readAllBytes()).order(LITTLE_ENDIAN);
            final int header = indexOf(data, VORBIS_IDENTIFICATION);
            final long frames = lastGranule(data);
            final int rate = header < 0 ? 0 : data.getInt(header + SAMPLE_RATE_OFFSET);
            if (frames < 0 || rate <= 0) {
                log.warn("{} has no readable length: taken as 0 s long", path);
                return 0;
            }
            return frames / (double) rate;
        } catch (final IOException | IndexOutOfBoundsException e) {
            log.warn("Could not read the length of {}: taken as 0 s long", path, e);
            return 0;
        }
    }

    /** The granule position of the last page that has one: the number of sample frames the file holds. */
    private long lastGranule(final ByteBuffer data) {
        for (int at = data.limit() - GRANULE_OFFSET - Long.BYTES; at >= 0; at--) {
            if (matchesAt(data, PAGE_START, at)) {
                final long granule = data.getLong(at + GRANULE_OFFSET);
                if (granule >= 0) {
                    return granule;
                }
            }
        }
        return -1;
    }

    private int indexOf(final ByteBuffer data, final byte[] pattern) {
        for (int at = 0; at + pattern.length <= data.limit(); at++) {
            if (matchesAt(data, pattern, at)) {
                return at;
            }
        }
        return -1;
    }

    private boolean matchesAt(final ByteBuffer data, final byte[] pattern, final int at) {
        for (int index = 0; index < pattern.length; index++) {
            if (data.get(at + index) != pattern[index]) {
                return false;
            }
        }
        return true;
    }
}
