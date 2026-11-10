package house.x1337.app.smb3.game.sound;

import com.jme3.audio.AudioBuffer;
import com.jme3.util.BufferUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SoundEffectTrimmer} on synthetic stereo PCM at 1000 Hz, so one frame is one millisecond.
 */
class SoundEffectTrimmerTest {
    private static final int RATE = 1000;
    private static final short LOUD = 8000;
    private static final short QUIET = 800;

    private final SoundEffectTrimmer trimmer = new SoundEffectTrimmer();

    @Test
    @DisplayName("The silence before and after the sound is cut, and the sound is kept sample for sample")
    void theSilenceAroundTheSoundIsCut() {
        // Prepare - 15 ms of silence, 100 ms of tone, 1000 ms of silence
        final AudioBuffer decoded = buffer(new Part(15, 0), new Part(100, LOUD), new Part(1000, 0));

        // Execute
        final AudioBuffer trimmed = trimmer.trim(decoded);

        // Verify
        final short[] samples = samples(trimmed);
        assertThat(samples).as("100 stereo frames").hasSize(200);
        assertThat(samples[0]).as("Starts on the first sounding sample").isEqualTo(LOUD);
        assertThat(samples[1]).as("Right channel too").isEqualTo(LOUD);
        assertThat(samples[2]).as("The tone goes on unchanged").isEqualTo((short) -LOUD);
        assertThat(trimmed.getChannels()).isEqualTo(2);
        assertThat(trimmed.getBitsPerSample()).isEqualTo(16);
        assertThat(trimmed.getSampleRate()).isEqualTo(RATE);
    }

    @Test
    @DisplayName("A short burst after a long silence at the end is a pop, and is cut with the silence")
    void aShortBurstAfterALongSilenceIsCut() {
        // Prepare - the tone, 118 ms of silence, then a 13 ms pop
        final AudioBuffer decoded = buffer(
            new Part(15, 0), new Part(100, LOUD), new Part(118, 0), new Part(13, QUIET), new Part(1000, 0)
        );

        // Execute
        final AudioBuffer trimmed = trimmer.trim(decoded);

        // Verify
        assertThat(samples(trimmed)).as("Only the tone's 100 frames").hasSize(200);
    }

    @Test
    @DisplayName("Every trailing pop is cut, down to the last real sound")
    void everyTrailingPopIsCut() {
        // Prepare - two separate kicks, then a pop 84 ms after the second
        final AudioBuffer decoded = buffer(
            new Part(15, 0), new Part(37, LOUD), new Part(62, 0), new Part(36, LOUD), new Part(84, 0),
            new Part(13, QUIET), new Part(1000, 0)
        );

        // Execute
        final AudioBuffer trimmed = trimmer.trim(decoded);

        // Verify
        assertThat(samples(trimmed)).as("Both kicks and the silence between them").hasSize((37 + 62 + 36) * 2);
    }

    @Test
    @DisplayName("A short final sound close behind the one before it is part of the effect")
    void aShortSoundCloseBehindIsKept() {
        // Prepare - 40 ms of silence is too short a gap for a pop
        final AudioBuffer decoded = buffer(new Part(100, LOUD), new Part(40, 0), new Part(13, QUIET));

        // Execute
        final AudioBuffer trimmed = trimmer.trim(decoded);

        // Verify
        assertThat(samples(trimmed)).hasSize((100 + 40 + 13) * 2);
    }

    @Test
    @DisplayName("A long final sound after a long silence is part of the effect")
    void aLongSoundAfterASilenceIsKept() {
        // Prepare - three falling notes, each after a 70 ms gap, like the power-down
        final AudioBuffer decoded = buffer(
            new Part(198, LOUD), new Part(70, 0), new Part(207, LOUD), new Part(70, 0), new Part(214, LOUD)
        );

        // Execute
        final AudioBuffer trimmed = trimmer.trim(decoded);

        // Verify
        assertThat(samples(trimmed)).hasSize((198 + 70 + 207 + 70 + 214) * 2);
    }

    @Test
    @DisplayName("Samples at the silence level count as silence")
    void samplesAtTheSilenceLevelAreSilence() {
        // Prepare - decoder noise just at the level, around 50 ms of tone
        final short noise = SoundEffectTrimmer.SILENCE_LEVEL;
        final AudioBuffer decoded = buffer(new Part(20, noise), new Part(50, LOUD), new Part(20, noise));

        // Execute
        final AudioBuffer trimmed = trimmer.trim(decoded);

        // Verify
        assertThat(samples(trimmed)).hasSize(100);
    }

    @Test
    @DisplayName("A silent buffer, or one that is not 16-bit, is returned untouched")
    void untrimmableBuffersAreReturnedAsTheyAre() {
        // Prepare
        final AudioBuffer silent = buffer(new Part(100, 0));
        final AudioBuffer eightBit = new AudioBuffer();
        eightBit.setupFormat(1, 8, RATE);
        eightBit.updateData(BufferUtils.createByteBuffer(10));

        // Execute & Verify
        assertThat(trimmer.trim(silent)).isSameAs(silent);
        assertThat(trimmer.trim(eightBit)).isSameAs(eightBit);
    }

    /** A run of frames: a square tone of the given amplitude, or silence for 0. */
    private record Part(int frames, short amplitude) {
        Part(final int frames, final int amplitude) {
            this(frames, (short) amplitude);
        }
    }

    private AudioBuffer buffer(final Part... parts) {
        int frames = 0;
        for (final Part part : parts) {
            frames += part.frames();
        }
        final ByteBuffer pcm = BufferUtils.createByteBuffer(frames * 4).order(LITTLE_ENDIAN);
        for (final Part part : parts) {
            for (int frame = 0; frame < part.frames(); frame++) {
                final short sample = (short) (frame % 2 == 0 ? part.amplitude() : -part.amplitude());
                pcm.putShort(sample).putShort(sample);
            }
        }
        pcm.flip();
        final AudioBuffer buffer = new AudioBuffer();
        buffer.setupFormat(2, 16, RATE);
        buffer.updateData(pcm);
        return buffer;
    }

    private short[] samples(final AudioBuffer buffer) {
        final ShortBuffer view = buffer.getData().duplicate().order(LITTLE_ENDIAN).asShortBuffer();
        final short[] samples = new short[view.remaining()];
        view.get(samples);
        return samples;
    }
}
