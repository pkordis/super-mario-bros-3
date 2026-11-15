package house.x1337.app.smb3.enumeration;

/**
 * How a sound effect claims its {@link SoundChannel} while another effect still sounds there.
 *
 * <ul>
 *     <li>{@link #CUTS}: stops the effect sounding and starts from its beginning, itself included.</li>
 *     <li>{@link #LOCKS}: cuts like {@link #CUTS}, then nothing else starts on the channel until it ends. Asked for
 *     again, it restarts.</li>
 *     <li>{@link #WAITS_FOR_SILENCE}: starts only on a silent channel, and is dropped otherwise.</li>
 *     <li>{@link #PLAYS_THROUGH}: cuts like {@link #CUTS}, then plays to its end: until then nothing starts on the
 *     channel, itself included, so asking for it on every tick plays it back to back.</li>
 * </ul>
 */
public enum SoundChannelRule {
    CUTS,
    LOCKS,
    PLAYS_THROUGH,
    WAITS_FOR_SILENCE
}
