package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.sound.SoundTrack;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.GameContext.MAP;
import static house.x1337.app.smb3.enumeration.SoundChannel.LEVEL_1;
import static house.x1337.app.smb3.enumeration.SoundChannel.LEVEL_2;
import static house.x1337.app.smb3.enumeration.SoundChannel.PLAYER;
import static house.x1337.app.smb3.enumeration.SoundChannel.WORLD_MAP;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.CUTS;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.LOCKS;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.PLAYS_THROUGH;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.WAITS_FOR_SILENCE;

/**
 * Every sound effect the game can play. Each constant holds the classpath asset path of its {@code .ogg} file, the
 * label the editor shows for it, the {@link GameContext} it belongs to, and the {@link SoundChannel} it plays on with
 * the {@link SoundChannelRule} it claims that channel by. The world map's sounds (moving across it, entering a level,
 * a level cleared, the map's Hammer Bros. and airship moving, the twirl) belong to {@link GameContext#MAP}, everything
 * else to {@link GameContext#LEVEL_SCENE}. {@link #POWER_ON} is one seamless cycle of the full P-meter's sound, made
 * to be looped.
 *
 * <p>Most effects cut whatever sounds on their channel, themselves included. The 1-up and the airship lock theirs
 * until they end, and the tail wag waits for a silent channel. The time-bonus beep, asked for on every tick, is
 * therefore restarted on every tick and heard as one continuous tone. The skid, also asked for on every tick, plays
 * through instead, so it is heard back to back for as long as the skid lasts. It is the same recording as the
 * {@link #TWIRL}.
 *
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#play(SoundEffect)
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#loop(SoundEffect)
 */
@Getter
@RequiredArgsConstructor
public enum SoundEffect implements SoundTrack {
    AIRSHIP_MOVEMENT("sound/effects/airship_movement.ogg", "Airship Movement", MAP, LEVEL_2, LOCKS),
    BLOCK("sound/effects/block.ogg", "Block", LEVEL_SCENE, PLAYER),
    BOWSER_FIREBALL("sound/effects/bowser_fireball.ogg", "Bowser's Fireball", LEVEL_SCENE, LEVEL_2),
    BREAKING_BLOCK("sound/effects/breaking_block.ogg", "Breaking a Block", LEVEL_SCENE, LEVEL_2),
    CANNONBALL("sound/effects/cannonball.ogg", "Cannonball", LEVEL_SCENE, LEVEL_1),
    CASINO_BEEP("sound/effects/casino_beep.ogg", "Casino Game Beep", LEVEL_SCENE, LEVEL_1),
    COIN("sound/effects/coin.ogg", "Coin", LEVEL_SCENE, LEVEL_1),
    ENEMY_STOMP("sound/effects/enemy_stomp.ogg", "Enemy Stomp", LEVEL_SCENE, PLAYER),
    FIREBALL("sound/effects/fireball.ogg", "Fireball", LEVEL_SCENE, PLAYER),
    HAMMER_BRO_MOVEMENT("sound/effects/hammer_bro_movement.ogg", "Hammer Bro Movement", MAP, LEVEL_2),
    JUMP("sound/effects/jump.ogg", "Jump", LEVEL_SCENE, PLAYER),
    KOOPA_SHELL("sound/effects/koopa_shell.ogg", "Koopa Shell", LEVEL_SCENE, PLAYER),
    LEVEL_CLEAR("sound/effects/level_clear.ogg", "Level Clear", MAP, WORLD_MAP),
    LEVEL_START("sound/effects/level_start.ogg", "Level Start", MAP, WORLD_MAP),
    MOVE_CURSOR("sound/effects/move_cursor.ogg", "Move Cursor", MAP, WORLD_MAP),
    N_SPADE_CLEAR("sound/effects/n_spade_clear.ogg", "N-Spade Clear", LEVEL_SCENE, WORLD_MAP),
    ONE_UP("sound/effects/one_up.ogg", "1-Up", LEVEL_SCENE, LEVEL_1, LOCKS),
    PAUSE("sound/effects/pause.ogg", "Pause", LEVEL_SCENE, SoundChannel.PAUSE),
    PLAYER_SPIN("sound/effects/player_spin.ogg", "Player Spin", MAP, WORLD_MAP),
    POWER_DOWN("sound/effects/power_down.ogg", "Power Down", LEVEL_SCENE, PLAYER),
    POWER_ON("sound/effects/power_on.ogg", "P-Meter Full", LEVEL_SCENE, PLAYER),
    POWER_UP("sound/effects/power_up.ogg", "Power Up", LEVEL_SCENE, LEVEL_1),
    POWER_UP_BLOCK("sound/effects/power_up_block.ogg", "Power Up (Block)", LEVEL_SCENE, LEVEL_1),
    SKID("sound/effects/skid.ogg", "Skid", LEVEL_SCENE, LEVEL_2, PLAYS_THROUGH),
    SUPER_JUMP("sound/effects/super_jump.ogg", "Super Jump", LEVEL_SCENE, PLAYER),
    TAIL_WAG("sound/effects/tail_wag.ogg", "Tail Wag", LEVEL_SCENE, LEVEL_1, WAITS_FOR_SILENCE),
    TANOOKI("sound/effects/tanooki.ogg", "Tanooki", LEVEL_SCENE, LEVEL_1),
    TWIRL("sound/effects/twirl.ogg", "Twirl", MAP, WORLD_MAP),
    VINE("sound/effects/vine.ogg", "Vine", LEVEL_SCENE, LEVEL_1),
    WORLD_START("sound/effects/world_start.ogg", "World Start", MAP, WORLD_MAP),
    WRONG("sound/effects/wrong.ogg", "Wrong", LEVEL_SCENE, WORLD_MAP);

    private final String path;
    private final String label;
    private final GameContext context;
    private final SoundChannel channel;
    private final SoundChannelRule channelRule;

    SoundEffect(
        final String path,
        final String label,
        final GameContext context,
        final SoundChannel channel
    ) {
        this(path, label, context, channel, CUTS);
    }
}
