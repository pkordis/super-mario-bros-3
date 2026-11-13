package house.x1337.app.smb3.game.sound;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioBuffer;
import com.jme3.audio.AudioContext;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioRenderer;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jme3.audio.AudioSource.Status.Playing;
import static com.jme3.audio.AudioSource.Status.Stopped;
import static house.x1337.app.smb3.enumeration.MusicTrack.COURSE_CLEAR;
import static house.x1337.app.smb3.enumeration.MusicTrack.HURRY_UP;
import static house.x1337.app.smb3.enumeration.MusicTrack.OVERWORLD;
import static house.x1337.app.smb3.enumeration.MusicTrack.WORLD_1_GRASS_LAND;
import static house.x1337.app.smb3.enumeration.SoundEffect.CASINO_BEEP;
import static house.x1337.app.smb3.enumeration.SoundEffect.COIN;
import static house.x1337.app.smb3.enumeration.SoundEffect.JUMP;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_ON;
import static house.x1337.app.smb3.enumeration.SoundEffect.TAIL_WAG;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SoundPlayer} against a mocked jME audio renderer, installed as the test thread's audio context the way the
 * engine installs its own on the update thread.
 */
class SoundPlayerTest {
    private static final int RATE = 1000;
    private static final int LEAD_IN_FRAMES = 15;
    private static final int SOUND_FRAMES = 100;
    private static final int TAIL_FRAMES = 400;
    private static final float FRAME_SECONDS = 1 / 60f;

    private final GameEventBus gameEventBus = new GameEventBus();
    private final AudioRenderer audioRenderer = mock(AudioRenderer.class);
    private final AssetManager assetManager = mock(AssetManager.class);
    private final Application application = mock(Application.class);
    private final SoundPlayer soundPlayer = new SoundPlayer(
        gameEventBus,
        new SoundEffectTrimmer(),
        new OggTrackLength()
    );
    private final List<SoundPlayComplete> completions = new ArrayList<>();

    @BeforeEach
    void prepare() {
        gameEventBus.subscribe(SoundPlayComplete.class, completions::add);
        when(assetManager.loadAsset(any(AudioKey.class))).thenAnswer(invocation -> {
            final AudioKey key = invocation.getArgument(0);
            return key.isStream() ? mock(AudioData.class) : decodedEffect();
        });
        when(application.getAssetManager()).thenReturn(assetManager);
        when(application.getAudioRenderer()).thenReturn(audioRenderer);
        AudioContext.setAudioRenderer(audioRenderer);
    }

    @AfterEach
    void tearDown() {
        AudioContext.setAudioRenderer(null);
    }

    @Test
    @DisplayName("Before any engine attaches, playing does nothing and does not throw")
    void beforeAttachingPlayingDoesNothing() {
        // Execute & Verify
        assertThatCode(() -> {
            soundPlayer.play(JUMP);
            soundPlayer.play(OVERWORLD);
            soundPlayer.stopMusic();
        }).doesNotThrowAnyException();
        verifyNoInteractions(assetManager, audioRenderer);
    }

    @Test
    @DisplayName("An engine without an audio renderer turns sound off without throwing")
    void anEngineWithoutAnAudioRendererTurnsSoundOff() {
        // Prepare
        when(application.getAudioRenderer()).thenReturn(null);
        soundPlayer.attach(application);

        // Execute & Verify
        assertThatCode(() -> {
            soundPlayer.play(JUMP);
            soundPlayer.play(OVERWORLD);
            soundPlayer.stopMusic();
        }).doesNotThrowAnyException();
        verifyNoInteractions(assetManager, audioRenderer);
    }

    @Test
    @DisplayName("Attaching decodes every effect once, buffered rather than streamed")
    void attachingDecodesEveryEffect() {
        // Execute
        soundPlayer.attach(application);

        // Verify
        final ArgumentCaptor<AudioKey> keys = ArgumentCaptor.forClass(AudioKey.class);
        verify(assetManager, times(SoundEffect.values().length)).loadAsset(keys.capture());
        assertThat(keys.getAllValues())
            .extracting(AudioKey::getName)
            .containsExactlyElementsOf(Arrays.stream(SoundEffect.values()).map(SoundEffect::getPath).toList());
        assertThat(keys.getAllValues()).extracting(AudioKey::isStream).as("Buffered").containsOnly(false);
        verifyNoInteractions(audioRenderer);
    }

    @Test
    @DisplayName("Playing an effect loads nothing more and starts a new copy of its trimmed, headspace node each time")
    void playingAnEffectStartsACopy() {
        // Prepare
        soundPlayer.attach(application);

        // Execute
        soundPlayer.play(JUMP);
        soundPlayer.play(JUMP);

        // Verify
        verify(assetManager, times(SoundEffect.values().length)).loadAsset(any(AudioKey.class));
        final ArgumentCaptor<AudioNode> node = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(2)).playSource(node.capture());
        verify(audioRenderer, never()).playSourceInstance(any(AudioNode.class));
        assertThat(node.getAllValues().get(0)).as("A copy each time").isNotSameAs(node.getValue());
        assertThat(node.getValue().isPositional()).as("Headspace audio").isFalse();
        assertThat(node.getValue().getAudioData()).as("The effect's own sound")
            .isSameAs(soundPlayer.getEffects().get(JUMP).getAudioData());
        final AudioBuffer data = (AudioBuffer) node.getValue().getAudioData();
        assertThat(data.getData().limit())
            .as("Only the sounding frames, 2 channels of 2 bytes each")
            .isEqualTo(SOUND_FRAMES * 4);
    }

    @Test
    @DisplayName("An effect that is not self-overlapping plays on its own node, and not again while that node sounds")
    void anEffectThatDoesNotOverlapItselfWaitsForItsEnd() {
        // Prepare
        soundPlayer.attach(application);

        // Execute - a press, a second press while it sounds, a third once it has ended
        soundPlayer.play(TAIL_WAG);
        final ArgumentCaptor<AudioNode> node = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer).playSource(node.capture());
        node.getValue().setStatus(Playing);
        soundPlayer.play(TAIL_WAG);
        node.getValue().setStatus(Stopped);
        soundPlayer.play(TAIL_WAG);

        // Verify
        verify(audioRenderer, times(2).description("The first press and the one after the end"))
            .playSource(node.getValue());
        verify(audioRenderer, never()).playSourceInstance(any(AudioNode.class));
    }

    @Test
    @DisplayName("Different effects play their own nodes")
    void differentEffectsPlayTheirOwnNodes() {
        // Prepare
        soundPlayer.attach(application);

        // Execute
        soundPlayer.play(JUMP);
        soundPlayer.play(COIN);

        // Verify
        final ArgumentCaptor<AudioNode> node = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(2)).playSource(node.capture());
        assertThat(node.getAllValues()).extracting(AudioNode::getAudioData).containsExactly(
            soundPlayer.getEffects().get(JUMP).getAudioData(),
            soundPlayer.getEffects().get(COIN).getAudioData()
        );
    }

    @Test
    @DisplayName("A track streams with a cache, loops and plays as the only track")
    void aTrackStreamsLoopsAndPlays() {
        // Prepare
        soundPlayer.attach(application);

        // Execute
        soundPlayer.play(OVERWORLD);

        // Verify
        final AudioKey key = streamedKeys().getFirst();
        assertThat(key.getName()).isEqualTo("sound/music/overworld.ogg");
        assertThat(key.useStreamCache()).as("Cached, so the loop can rewind").isTrue();
        final ArgumentCaptor<AudioNode> node = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer).playSource(node.capture());
        assertThat(node.getValue().isLooping()).as("Loops").isTrue();
        assertThat(node.getValue().isPositional()).as("Headspace audio").isFalse();
    }

    @Test
    @DisplayName("Starting a track stops the one playing, and stopping the music stops the new one")
    void startingATrackStopsTheCurrentOne() {
        // Prepare
        soundPlayer.attach(application);
        soundPlayer.play(OVERWORLD);

        // Execute
        soundPlayer.play(WORLD_1_GRASS_LAND);
        soundPlayer.stopMusic();
        soundPlayer.stopMusic();

        // Verify
        final ArgumentCaptor<AudioNode> played = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(2)).playSource(played.capture());
        final ArgumentCaptor<AudioNode> stopped = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(2)).stopSource(stopped.capture());
        final List<AudioNode> tracks = played.getAllValues();
        assertThat(stopped.getAllValues())
            .as("The first track when the second starts, then the second; a second stop does nothing")
            .containsExactly(tracks.get(0), tracks.get(1));
    }

    @Test
    @DisplayName("A jingle plays once instead of looping")
    void aJinglePlaysOnce() {
        // Prepare
        soundPlayer.attach(application);

        // Execute
        soundPlayer.play(COURSE_CLEAR);

        // Verify
        final ArgumentCaptor<AudioNode> node = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer).playSource(node.capture());
        assertThat(node.getValue().isLooping()).as("Plays once").isFalse();
    }

    @Test
    @DisplayName("The current track follows what was started and stopped")
    void theCurrentTrackFollowsPlayAndStop() {
        // Prepare
        soundPlayer.attach(application);
        final MusicTrack beforePlaying = soundPlayer.getCurrentTrack();

        // Execute
        soundPlayer.play(OVERWORLD);
        final MusicTrack afterTheFirst = soundPlayer.getCurrentTrack();
        soundPlayer.play(WORLD_1_GRASS_LAND);
        final MusicTrack afterTheSecond = soundPlayer.getCurrentTrack();
        soundPlayer.stopMusic();

        // Verify
        assertThat(beforePlaying).as("Nothing before the first track").isNull();
        assertThat(afterTheFirst).isEqualTo(OVERWORLD);
        assertThat(afterTheSecond).isEqualTo(WORLD_1_GRASS_LAND);
        assertThat(soundPlayer.getCurrentTrack()).as("Nothing after the stop").isNull();
    }

    @Test
    @DisplayName("Without an engine no track is current, even after asking for one")
    void withoutAnEngineNoTrackIsCurrent() {
        // Execute
        soundPlayer.play(OVERWORLD);

        // Verify
        assertThat(soundPlayer.getCurrentTrack()).isNull();
    }

    @Test
    @DisplayName("Once the engine stops nothing plays, and the next engine loads its effects afresh")
    void theStoppedEngineIsForgotten() {
        // Prepare
        soundPlayer.attach(application);
        soundPlayer.play(JUMP);
        gameEventBus.publish(new GameEngineStopped());

        // Execute
        soundPlayer.play(JUMP);
        soundPlayer.play(OVERWORLD);
        soundPlayer.attach(application);
        soundPlayer.play(JUMP);

        // Verify
        assertThat(streamedKeys()).as("The track asked for once the engine stopped").isEmpty();
        verify(assetManager, times(2 * SoundEffect.values().length).description("Every effect, once per engine"))
            .loadAsset(any(AudioKey.class));
        verify(audioRenderer, times(2).description("Once before the stop, once on the next engine"))
            .playSource(any(AudioNode.class));
    }

    @Test
    @DisplayName("A mute stops every effect still sounding, its loops included, and leaves the music playing")
    void aMuteStopsTheEffectsSounding() {
        // Prepare - the music, the P ringing, a jump and a tail wag all sounding
        soundPlayer.attach(application);
        soundPlayer.play(OVERWORLD);
        soundPlayer.loop(POWER_ON);
        soundPlayer.play(JUMP);
        soundPlayer.play(TAIL_WAG);
        final ArgumentCaptor<AudioNode> played = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(4)).playSource(played.capture());
        played.getAllValues().forEach(node -> node.setStatus(Playing));
        final List<AudioNode> nodes = played.getAllValues();

        // Execute
        soundPlayer.muteEffectsExcept(CASINO_BEEP);

        // Verify
        final ArgumentCaptor<AudioNode> stopped = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(3)).stopSource(stopped.capture());
        assertThat(stopped.getAllValues()).as("The loop, the jump and the tail wag")
            .containsExactlyInAnyOrder(nodes.get(1), nodes.get(2), nodes.get(3));
        assertThat(soundPlayer.getCurrentTrack()).as("The music plays on").isEqualTo(OVERWORLD);
    }

    @Test
    @DisplayName("While muted, only the effects let through play, and no loop starts")
    void whileMutedOnlyTheEffectsLetThroughPlay() {
        // Prepare
        soundPlayer.attach(application);
        soundPlayer.muteEffectsExcept(CASINO_BEEP);

        // Execute
        soundPlayer.play(COIN);
        soundPlayer.play(TAIL_WAG);
        soundPlayer.loop(POWER_ON);
        soundPlayer.play(CASINO_BEEP);

        // Verify
        final ArgumentCaptor<AudioNode> played = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer).playSource(played.capture());
        assertThat(played.getValue().getAudioData()).as("Only the beep")
            .isSameAs(soundPlayer.getEffects().get(CASINO_BEEP).getAudioData());
    }

    @Test
    @DisplayName("Unmuted, or on the next engine, every effect plays and loops again")
    void unmutedEveryEffectPlaysAgain() {
        // Prepare
        soundPlayer.attach(application);
        soundPlayer.muteEffectsExcept(CASINO_BEEP);

        // Execute
        soundPlayer.unmuteEffects();
        soundPlayer.play(COIN);
        soundPlayer.loop(POWER_ON);
        soundPlayer.stopLoop(POWER_ON);
        soundPlayer.muteEffectsExcept(CASINO_BEEP);
        soundPlayer.attach(application);
        soundPlayer.play(JUMP);

        // Verify
        final ArgumentCaptor<AudioNode> played = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer, times(3)).playSource(played.capture());
        assertThat(played.getAllValues().get(1).isLooping()).as("The loop").isTrue();
    }

    @Test
    @DisplayName("A jingle that plays to its end publishes SoundPlayComplete once, on the frame its node stops")
    void aJingleThatEndsPublishesItsCompletionOnce() {
        // Prepare
        soundPlayer.attach(application);
        soundPlayer.play(COURSE_CLEAR);
        final ArgumentCaptor<AudioNode> node = ArgumentCaptor.forClass(AudioNode.class);
        verify(audioRenderer).playSource(node.capture());
        node.getValue().setStatus(Playing);

        // Execute
        soundPlayer.update(FRAME_SECONDS);
        final List<SoundPlayComplete> whilePlaying = List.copyOf(completions);
        node.getValue().setStatus(Stopped);
        soundPlayer.update(FRAME_SECONDS);
        soundPlayer.update(FRAME_SECONDS);

        // Verify
        assertThat(whilePlaying).as("Not while it plays").isEmpty();
        assertThat(completions).as("Once, at its end").containsExactly(new SoundPlayComplete(COURSE_CLEAR));
    }

    @Test
    @DisplayName("A jingle stopped or replaced before its end publishes nothing, and a looping track never ends")
    void aCutJingleAndALoopPublishNothing() {
        // Prepare
        soundPlayer.attach(application);

        // Execute
        soundPlayer.play(COURSE_CLEAR);
        soundPlayer.stopMusic();
        soundPlayer.update(FRAME_SECONDS);
        soundPlayer.play(HURRY_UP);
        soundPlayer.play(OVERWORLD);
        soundPlayer.update(FRAME_SECONDS);

        // Verify
        assertThat(completions).isEmpty();
    }

    @Test
    @DisplayName("Without an audio renderer a jingle still ends, once its length, read from its file, has passed")
    void withoutARendererAJingleEndsAfterItsLength() {
        // Prepare - the hurry-up file is 2.784 s long
        when(application.getAudioRenderer()).thenReturn(null);
        soundPlayer.attach(application);
        soundPlayer.play(HURRY_UP);

        // Execute
        soundPlayer.update(2.7f);
        final List<SoundPlayComplete> beforeItsEnd = List.copyOf(completions);
        soundPlayer.update(0.1f);

        // Verify
        assertThat(beforeItsEnd).as("2.7 s in").isEmpty();
        assertThat(completions).as("2.8 s in").containsExactly(new SoundPlayComplete(HURRY_UP));
        assertThat(soundPlayer.getCurrentTrack()).as("Still the last track started").isEqualTo(HURRY_UP);
        verifyNoInteractions(assetManager, audioRenderer);
    }

    @Test
    @DisplayName("Without an audio renderer a looping track never ends")
    void withoutARendererALoopNeverEnds() {
        // Prepare
        when(application.getAudioRenderer()).thenReturn(null);
        soundPlayer.attach(application);
        soundPlayer.play(OVERWORLD);

        // Execute
        soundPlayer.update(1000f);

        // Verify
        assertThat(completions).isEmpty();
    }

    private List<AudioKey> streamedKeys() {
        final ArgumentCaptor<AudioKey> keys = ArgumentCaptor.forClass(AudioKey.class);
        verify(assetManager, atLeastOnce()).loadAsset(keys.capture());
        return keys.getAllValues().stream().filter(AudioKey::isStream).toList();
    }

    /** A stereo effect as the decoder hands it over: silence, a square tone, then a long silent tail. */
    private AudioBuffer decodedEffect() {
        final int frames = LEAD_IN_FRAMES + SOUND_FRAMES + TAIL_FRAMES;
        final ByteBuffer pcm = BufferUtils.createByteBuffer(frames * 4).order(LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++) {
            final boolean sounding = frame >= LEAD_IN_FRAMES && frame < LEAD_IN_FRAMES + SOUND_FRAMES;
            final short sample = sounding ? (short) (frame % 2 == 0 ? 8000 : -8000) : 0;
            pcm.putShort(sample).putShort(sample);
        }
        pcm.flip();
        final AudioBuffer buffer = new AudioBuffer();
        buffer.setupFormat(2, 16, RATE);
        buffer.updateData(pcm);
        return buffer;
    }
}
