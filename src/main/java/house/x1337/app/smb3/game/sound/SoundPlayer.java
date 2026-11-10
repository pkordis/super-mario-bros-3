package house.x1337.app.smb3.game.sound;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioBuffer;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

import static com.jme3.audio.AudioData.DataType.Stream;

/**
 * Plays the game's sound effects and music through the running engine's audio renderer.
 *
 * <p>Effects are fire and forget: each {@link #play(SoundEffect)} starts a new instance, so the same effect can
 * overlap itself, and no effect stops the music. Every effect is decoded into memory and trimmed by
 * {@link SoundEffectTrimmer} when an engine attaches, so the first play of each one is as quick as the rest.
 * Music plays one track at a time and loops; it is streamed, with the stream cached so it can rewind to the start.
 *
 * <p>The player is a singleton and outlives each engine, so every engine hands itself over through
 * {@link #attach(Application)} as it starts. Until then, and on an engine without an audio renderer (a headless
 * one, for example), every call does nothing. Calls must come from the engine's update thread: jME only knows
 * the audio renderer on that thread.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public final class SoundPlayer {
    private final GameEventBus gameEventBus;
    private final SoundEffectTrimmer soundEffectTrimmer;
    private final Map<SoundEffect, AudioNode> effects = new EnumMap<>(SoundEffect.class);

    private AssetManager assetManager;
    private AudioNode music;

    /**
     * Starts playing for the given engine: drops whatever was kept for the previous one, loads every effect, and
     * lets go of this engine again once it stops. Called once by each engine as it starts. The subscription is
     * renewed every time because an engine's {@code destroy()} purges the bus.
     *
     * @param application the engine whose asset manager loads the sounds
     */
    public void attach(final Application application) {
        detach();
        if (application.getAudioRenderer() == null) {
            log.info("No audio renderer: sound is off for this engine");
            return;
        }
        assetManager = application.getAssetManager();
        final long start = System.nanoTime();
        for (final SoundEffect effect : SoundEffect.values()) {
            effects.put(effect, loadEffect(effect));
        }
        log.debug("Loaded {} sound effects in {} ms", effects.size(), (System.nanoTime() - start) / 1_000_000);
        gameEventBus.subscribe(GameEngineStopped.class, event -> detach());
    }

    /**
     * Plays a sound effect over whatever is already playing.
     *
     * @param effect the effect to play
     */
    public void play(final SoundEffect effect) {
        final AudioNode node = effects.get(effect);
        if (node != null) {
            node.playInstance();
        }
    }

    /**
     * Stops the current track, if any, and loops the given one from its start.
     *
     * @param track the track to play
     */
    public void play(final MusicTrack track) {
        if (assetManager == null) {
            return;
        }
        stopMusic();
        final AudioNode node = new AudioNode(assetManager, track.getPath(), Stream);
        node.setPositional(false);
        node.setLooping(true);
        node.play();
        music = node;
    }

    /** Stops the current track, if any. */
    public void stopMusic() {
        if (music != null) {
            music.stop();
            music = null;
        }
    }

    /**
     * Forgets the stopped engine's sounds. Nothing is stopped here: the engine may already be gone, and its audio
     * renderer stops every source it still plays when it shuts down.
     */
    private void detach() {
        effects.clear();
        music = null;
        assetManager = null;
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
}
