package house.x1337.app.smb3.game.sound;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioBuffer;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.enumeration.SoundChannel;
import house.x1337.app.smb3.enumeration.SoundChannelRule;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jme3.audio.AudioData.DataType.Stream;
import static com.jme3.audio.AudioSource.Status.Playing;
import static com.jme3.audio.AudioSource.Status.Stopped;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.LOCKS;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.WAITS_FOR_SILENCE;

/**
 * Plays the game's sound effects and music through the running engine's audio renderer.
 *
 * <p>Effects play on channels ({@link SoundChannel}). A channel sounds one effect at a time and the channels sound
 * together, so a coin and a jump are heard at once but two coins are not. Each effect claims its channel by its
 * {@link SoundChannelRule}: most cut the effect sounding there, a locking one keeps the channel until it ends, and
 * the tail wag waits for silence. A {@link #loop(SoundEffect) loop} is the quietest sound
 * on its channel: any effect played there stops it, and it starts again on the first frame the channel is silent,
 * until {@link #stopLoop(SoundEffect)}. Every effect is decoded into memory and trimmed by
 * {@link SoundEffectTrimmer} when an engine attaches, so the first play of each one is as quick as the rest. Music
 * plays one track at a time alongside every channel, looped unless the track is a jingle; it is streamed, with the
 * stream cached so it can rewind to the start.
 *
 * <p>The effects can be muted, all but a few, through {@link #muteEffectsExcept(SoundEffect...)}: every effect still
 * sounding stops at once, loops included, and until {@link #unmuteEffects()} a muted effect neither plays nor loops.
 * The music is not muted. Each play is a node of its own rather than an instance of the effect's node precisely so it
 * can be stopped: jME cannot stop an instance.
 *
 * <p>A jingle that plays to its end publishes {@link SoundPlayComplete}, from {@link #update(float)} on the engine's
 * update thread. A jingle stopped or replaced first publishes nothing. On an engine without an audio renderer (a
 * headless one, for example) nothing is heard, but a jingle still ends: once its length, read from its file by
 * {@link OggTrackLength}, has passed. So the game moves on at the same moments with or without a sound device.
 *
 * <p>The player is a singleton and outlives each engine, so every engine hands itself over through
 * {@link #attach(Application)} as it starts. Until then every call does nothing. Calls must come from the engine's
 * update thread: jME only knows the audio renderer on that thread.
 */
@Data
@Slf4j
@Singleton
@RequiredArgsConstructor
public final class SoundPlayer {
    private final GameEventBus gameEventBus;
    private final SoundEffectTrimmer soundEffectTrimmer;
    private final OggTrackLength oggTrackLength;
    private final Map<SoundEffect, AudioNode> effects = new EnumMap<>(SoundEffect.class);
    private final Map<SoundChannel, ChannelSound> channelSounds = new EnumMap<>(SoundChannel.class);
    private final Map<SoundEffect, AudioNode> loops = new EnumMap<>(SoundEffect.class);
    private final Set<SoundEffect> heldLoops = EnumSet.noneOf(SoundEffect.class);
    private final Set<SoundEffect> heardWhileMuted = EnumSet.noneOf(SoundEffect.class);

    private boolean attached;
    private boolean effectsMuted;
    private double clockSeconds;
    private AssetManager assetManager;
    private AudioNode music;
    private MusicTrack currentTrack;
    private PlayingJingle jingle;

    /**
     * Starts playing for the given engine: drops whatever was kept for the previous one, loads every effect, and
     * lets go of this engine again once it stops. Called once by each engine as it starts. The subscription is
     * renewed every time because an engine's {@code destroy()} purges the bus.
     *
     * @param application the engine whose asset manager loads the sounds
     */
    public void attach(final Application application) {
        detach();
        attached = true;
        gameEventBus.subscribe(GameEngineStopped.class, event -> detach());
        if (application.getAudioRenderer() == null) {
            log.info("No audio renderer: sound is off for this engine, and jingles end after their length");
            return;
        }
        assetManager = application.getAssetManager();
        final long start = System.nanoTime();
        for (final SoundEffect effect : SoundEffect.values()) {
            effects.put(effect, loadEffect(effect));
        }
        log.debug("Loaded {} sound effects in {} ms", effects.size(), (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * Publishes {@link SoundPlayComplete} once the current jingle has played to its end, and starts again every loop
     * whose channel has fallen silent. Called once per frame by the engine, on its update thread.
     *
     * @param timePerFrame the time since the previous frame, in seconds
     */
    public void update(final float timePerFrame) {
        clockSeconds += timePerFrame;
        if (jingle != null && jingle.hasEnded(clockSeconds)) {
            final MusicTrack ended = jingle.track();
            jingle = null;
            gameEventBus.publish(new SoundPlayComplete(ended));
        }
        heldLoops.removeIf(effect -> {
            if (soundingOn(effect.getChannel()) != null) {
                return false;
            }
            loops.get(effect).play();
            return true;
        });
    }

    /**
     * Plays a sound effect on its channel, by the effect's {@link SoundChannelRule}: it stops the effect sounding there
     * and starts from its beginning, unless that effect locks the channel, or unless it waits for silence and the
     * channel is not silent. A loop on the channel stops either way and waits for the channel to fall silent.
     *
     * @param effect the effect to play
     */
    public void play(final SoundEffect effect) {
        final AudioNode effectNode = effects.get(effect);
        if (effectNode == null || isMuted(effect)) {
            return;
        }
        final SoundChannel channel = effect.getChannel();
        final ChannelSound sounding = soundingOn(channel);
        if (sounding != null && !sounding.yieldsTo(effect)) {
            return;
        }
        if (sounding != null) {
            sounding.node().stop();
        }
        holdTheLoopsOn(channel);
        final AudioNode node = copyOf(effect, effectNode);
        node.play();
        channelSounds.put(channel, new ChannelSound(effect, node));
    }

    /**
     * Plays a sound effect over and over, with no gap between the repeats, until {@link #stopLoop(SoundEffect)}. It
     * sounds only while no other effect sounds on its channel. Does nothing while the effect already loops, or while
     * it is muted.
     *
     * @param effect the effect to loop
     */
    public void loop(final SoundEffect effect) {
        final AudioNode effectNode = effects.get(effect);
        if (effectNode == null || loops.containsKey(effect) || isMuted(effect)) {
            return;
        }
        final AudioNode node = copyOf(effect, effectNode);
        node.setLooping(true);
        loops.put(effect, node);
        if (soundingOn(effect.getChannel()) == null) {
            node.play();
        } else {
            heldLoops.add(effect);
        }
    }

    /**
     * Stops the loop of a sound effect, whether it sounds or waits for its channel. Does nothing when the effect does
     * not loop.
     *
     * @param effect the effect whose loop stops
     */
    public void stopLoop(final SoundEffect effect) {
        final AudioNode node = loops.remove(effect);
        if (node != null && !heldLoops.remove(effect)) {
            node.stop();
        }
    }

    /**
     * Stops every effect still sounding, loops included, and keeps every effect but the given ones from playing or
     * looping until {@link #unmuteEffects()}. The music plays on.
     *
     * @param stillHeard the effects that play on through the mute
     */
    public void muteEffectsExcept(final SoundEffect... stillHeard) {
        effectsMuted = true;
        heardWhileMuted.clear();
        heardWhileMuted.addAll(List.of(stillHeard));
        loops.forEach((effect, node) -> {
            if (!heldLoops.contains(effect)) {
                node.stop();
            }
        });
        loops.clear();
        heldLoops.clear();
        channelSounds.values().stream()
            .map(ChannelSound::node)
            .filter(node -> node.getStatus() == Playing)
            .forEach(AudioNode::stop);
        channelSounds.clear();
    }

    /** Lets every effect play again after {@link #muteEffectsExcept(SoundEffect...)}. */
    public void unmuteEffects() {
        effectsMuted = false;
        heardWhileMuted.clear();
    }

    /**
     * Stops the current track, if any, and plays the given one from its start: looped when the track loops, once
     * when it is a jingle, which publishes {@link SoundPlayComplete} at its end.
     *
     * @param track the track to play
     */
    public void play(final MusicTrack track) {
        if (!attached) {
            return;
        }
        stopMusic();
        currentTrack = track;
        if (assetManager != null) {
            final AudioNode node = new AudioNode(assetManager, track.getPath(), Stream);
            node.setPositional(false);
            node.setLooping(track.isLooping());
            node.play();
            music = node;
        }
        if (!track.isLooping()) {
            jingle = music != null
                ? new AudibleJingle(track, music)
                : new SilentJingle(track, clockSeconds + oggTrackLength.secondsOf(track.getPath()));
        }
    }

    /** Stops the current track, if any. A jingle stopped here never publishes {@link SoundPlayComplete}. */
    public void stopMusic() {
        if (music != null) {
            music.stop();
            music = null;
        }
        currentTrack = null;
        jingle = null;
    }

    /**
     * Forgets the stopped engine's sounds. Nothing is stopped here: the engine may already be gone, and its audio
     * renderer stops every source it still plays when it shuts down.
     */
    private void detach() {
        effects.clear();
        channelSounds.clear();
        loops.clear();
        heldLoops.clear();
        unmuteEffects();
        attached = false;
        music = null;
        currentTrack = null;
        jingle = null;
        assetManager = null;
    }

    private boolean isMuted(final SoundEffect effect) {
        return effectsMuted && !heardWhileMuted.contains(effect);
    }

    private ChannelSound soundingOn(final SoundChannel channel) {
        final ChannelSound sound = channelSounds.get(channel);
        return sound != null && sound.node().getStatus() == Playing ? sound : null;
    }

    private void holdTheLoopsOn(final SoundChannel channel) {
        loops.forEach((effect, node) -> {
            if (effect.getChannel() == channel && heldLoops.add(effect)) {
                node.stop();
            }
        });
    }

    private AudioNode copyOf(
        final SoundEffect effect,
        final AudioNode effectNode
    ) {
        final AudioNode copy = new AudioNode(effectNode.getAudioData(), new AudioKey(effect.getPath(), false));
        copy.setPositional(false);
        return copy;
    }

    private AudioNode loadEffect(final SoundEffect effect) {
        final AudioKey key = new AudioKey(effect.getPath(), false);
        final AudioData decoded = assetManager.loadAsset(key);
        final AudioData data = decoded instanceof final AudioBuffer buffer
            ? soundEffectTrimmer.trim(buffer)
            : decoded;
        final AudioNode node = new AudioNode(data, key);
        node.setPositional(false);
        return node;
    }

    /** The effect a channel last started, which sounds for as long as its node plays. */
    private record ChannelSound(
        SoundEffect effect,
        AudioNode node
    ) {
        boolean yieldsTo(final SoundEffect next) {
            if (next.getChannelRule() == WAITS_FOR_SILENCE) {
                return false;
            }
            return next == effect || effect.getChannelRule() != LOCKS;
        }
    }

    /** A jingle under way, which knows when it has played to its end. */
    private sealed interface PlayingJingle permits AudibleJingle, SilentJingle {
        boolean hasEnded(double clockSeconds);
        MusicTrack track();
    }

    /** A heard jingle: it has ended once the renderer has stopped its node, which it does at the stream's end. */
    private record AudibleJingle(MusicTrack track, AudioNode node) implements PlayingJingle {
        @Override
        public boolean hasEnded(final double clockSeconds) {
            return node.getStatus() == Stopped;
        }
    }

    /** A jingle with no renderer to play it: it has ended once its length has passed. */
    private record SilentJingle(MusicTrack track, double endsAtSeconds) implements PlayingJingle {
        @Override
        public boolean hasEnded(final double clockSeconds) {
            return clockSeconds >= endsAtSeconds;
        }
    }
}
