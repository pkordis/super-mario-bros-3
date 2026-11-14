package house.x1337.app.smb3.enumeration;

/**
 * The channel a sound effect plays on. A channel sounds one effect at a time, and the channels sound together.
 * {@link #PLAYER} carries the player's own sounds (jump, bump, stomp, kick, pipe, fireball, the full P-meter's ring),
 * {@link #LEVEL_1} the rewards and most level sounds, {@link #LEVEL_2} the noisy ones (bricks, flames, the map's
 * airship and Hammer Bros.), {@link #WORLD_MAP} the world map's own sounds and {@link #PAUSE} the pause chime. Music
 * is not on any of them: it plays alongside every channel.
 *
 * @see SoundChannelRule
 */
public enum SoundChannel {
    PLAYER,
    LEVEL_1,
    LEVEL_2,
    WORLD_MAP,
    PAUSE
}
