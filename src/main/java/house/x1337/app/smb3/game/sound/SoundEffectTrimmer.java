package house.x1337.app.smb3.game.sound;

import com.jme3.audio.AudioBuffer;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.annotation.Singleton;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * Cuts a decoded sound effect down to the part that sounds, so it is heard the moment it is played.
 *
 * <p>The recordings start with a few milliseconds of silence and end with about a second of it, and a few of them
 * (the jumps, the shell) also carry a faint pop, recorded well after the sound itself has ended. The trim keeps
 * everything from the first sounding sample to the last one, and drops such a pop: a final burst of at most
 * {@value #POP_MAX_LENGTH_MS} ms that follows at least {@value #POP_MIN_GAP_MS} ms of silence.
 *
 * <p>A sample of magnitude {@value #SILENCE_LEVEL} or less (about -70 dBFS) is silence, and two sounding stretches
 * less than {@value #JOIN_GAP_MS} ms apart are one sound.
 *
 * <p>Works on 16-bit little-endian PCM, which is what jME's Ogg Vorbis decoder produces. Anything else, and a
 * buffer that is silent throughout, is returned as it is.
 */
@Singleton
public final class SoundEffectTrimmer {
    static final int SILENCE_LEVEL = 10;
    static final int JOIN_GAP_MS = 20;
    static final int POP_MAX_LENGTH_MS = 20;
    static final int POP_MIN_GAP_MS = 50;

    private static final int BITS_PER_SAMPLE = 16;
    private static final int BYTES_PER_SAMPLE = 2;

    /**
     * @param decoded a fully decoded effect
     * @return a new buffer holding only the part of {@code decoded} that sounds, in the same format; or
     *         {@code decoded} itself when it cannot be trimmed
     */
    public AudioBuffer trim(final AudioBuffer decoded) {
        if (decoded.getBitsPerSample() != BITS_PER_SAMPLE || decoded.getData() == null) {
            return decoded;
        }
        final int channels = decoded.getChannels();
        final int rate = decoded.getSampleRate();
        final ByteBuffer pcm = decoded.getData().duplicate().order(LITTLE_ENDIAN);
        pcm.position(0);
        final List<Segment> segments = segments(pcm.asShortBuffer(), channels, frames(JOIN_GAP_MS, rate));
        if (segments.isEmpty()) {
            return decoded;
        }
        while (segments.size() > 1 && isPop(segments.getLast(), segments.get(segments.size() - 2), rate)) {
            segments.removeLast();
        }

        final int frameBytes = channels * BYTES_PER_SAMPLE;
        final ByteBuffer kept = pcm.duplicate();
        kept.position(segments.getFirst().start() * frameBytes);
        kept.limit((segments.getLast().end() + 1) * frameBytes);
        final ByteBuffer trimmed = BufferUtils.createByteBuffer(kept.remaining());
        trimmed.put(kept).flip();

        final AudioBuffer result = new AudioBuffer();
        result.setupFormat(channels, BITS_PER_SAMPLE, rate);
        result.updateData(trimmed);
        return result;
    }

    /** The sounding stretches, in order, joined across silences shorter than {@code joinGap} frames. */
    private List<Segment> segments(final ShortBuffer samples, final int channels, final int joinGap) {
        final List<Segment> segments = new ArrayList<>();
        final int frames = samples.remaining() / channels;
        int start = -1;
        int last = -1;
        for (int frame = 0; frame < frames; frame++) {
            if (!isSounding(samples, channels, frame)) {
                continue;
            }
            if (start < 0) {
                start = frame;
            } else if (frame - last > joinGap) {
                segments.add(new Segment(start, last));
                start = frame;
            }
            last = frame;
        }
        if (start >= 0) {
            segments.add(new Segment(start, last));
        }
        return segments;
    }

    private boolean isSounding(final ShortBuffer samples, final int channels, final int frame) {
        for (int channel = 0; channel < channels; channel++) {
            if (Math.abs(samples.get(frame * channels + channel)) > SILENCE_LEVEL) {
                return true;
            }
        }
        return false;
    }

    private boolean isPop(final Segment last, final Segment previous, final int rate) {
        final int length = last.end() - last.start() + 1;
        final int gap = last.start() - previous.end() - 1;
        return length <= frames(POP_MAX_LENGTH_MS, rate) && gap >= frames(POP_MIN_GAP_MS, rate);
    }

    private int frames(final int milliseconds, final int rate) {
        return rate * milliseconds / 1000;
    }

    /** A sounding stretch, from its first to its last sounding frame, both included. */
    private record Segment(int start, int end) {
    }
}
