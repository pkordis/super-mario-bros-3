package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.sound.SoundTrack;
import lombok.Getter;

import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.GameContext.MAP;

/**
 * Every sound effect the game can play. Each constant holds the classpath asset path of its {@code .ogg} file, the
 * label the editor shows for it, and the {@link GameContext} it belongs to: the world map's sounds (moving across it,
 * entering a level, a level cleared, the map's Hammer Bros. and airship moving, the twirl) to {@link GameContext#MAP},
 * everything else to {@link GameContext#LEVEL_SCENE}. {@link #POWER_ON} is one seamless cycle of the full P-meter's
 * sound, made to be looped.
 *
 * <p>Every effect overlaps everything else that plays. Most also overlap themselves: each play starts a new copy. One
 * that is not self-overlapping ({@link #TAIL_WAG}) ignores a play while it is still sounding, so pressing faster than
 * it lasts is heard once per clip length rather than once per press.
 *
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#play(SoundEffect)
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#loop(SoundEffect)
 */
@Getter
public enum SoundEffect implements SoundTrack {
    AIRSHIP_MOVEMENT("sound/effects/airship_movement.ogg", "Airship Movement", MAP),
    BLOCK("sound/effects/block.ogg", "Block", LEVEL_SCENE),
    BOWSER_FIREBALL("sound/effects/bowser_fireball.ogg", "Bowser's Fireball", LEVEL_SCENE),
    BREAKING_BLOCK("sound/effects/breaking_block.ogg", "Breaking a Block", LEVEL_SCENE),
    CANNONBALL("sound/effects/cannonball.ogg", "Cannonball", LEVEL_SCENE),
    CASINO_BEEP("sound/effects/casino_beep.ogg", "Casino Game Beep", LEVEL_SCENE),
    COIN("sound/effects/coin.ogg", "Coin", LEVEL_SCENE),
    ENEMY_STOMP("sound/effects/enemy_stomp.ogg", "Enemy Stomp", LEVEL_SCENE),
    FIREBALL("sound/effects/fireball.ogg", "Fireball", LEVEL_SCENE),
    HAMMER_BRO_MOVEMENT("sound/effects/hammer_bro_movement.ogg", "Hammer Bro Movement", MAP),
    JUMP("sound/effects/jump.ogg", "Jump", LEVEL_SCENE),
    KOOPA_SHELL("sound/effects/koopa_shell.ogg", "Koopa Shell", LEVEL_SCENE),
    LEVEL_CLEAR("sound/effects/level_clear.ogg", "Level Clear", MAP),
    LEVEL_START("sound/effects/level_start.ogg", "Level Start", MAP),
    MOVE_CURSOR("sound/effects/move_cursor.ogg", "Move Cursor", MAP),
    N_SPADE_CLEAR("sound/effects/n_spade_clear.ogg", "N-Spade Clear", LEVEL_SCENE),
    ONE_UP("sound/effects/one_up.ogg", "1-Up", LEVEL_SCENE),
    PAUSE("sound/effects/pause.ogg", "Pause", LEVEL_SCENE),
    PLAYER_SPIN("sound/effects/player_spin.ogg", "Player Spin", MAP),
    POWER_DOWN("sound/effects/power_down.ogg", "Power Down", LEVEL_SCENE),
    POWER_ON("sound/effects/power_on.ogg", "P-Meter Full", LEVEL_SCENE),
    POWER_UP("sound/effects/power_up.ogg", "Power Up", LEVEL_SCENE),
    POWER_UP_BLOCK("sound/effects/power_up_block.ogg", "Power Up (Block)", LEVEL_SCENE),
    SUPER_JUMP("sound/effects/super_jump.ogg", "Super Jump", LEVEL_SCENE),
    TAIL_WAG("sound/effects/tail_wag.ogg", "Tail Wag", LEVEL_SCENE, false),
    TANOOKI("sound/effects/tanooki.ogg", "Tanooki", LEVEL_SCENE),
    TWIRL("sound/effects/twirl.ogg", "Twirl", MAP),
    VINE("sound/effects/vine.ogg", "Vine", LEVEL_SCENE),
    WORLD_START("sound/effects/world_start.ogg", "World Start", MAP),
    WRONG("sound/effects/wrong.ogg", "Wrong", LEVEL_SCENE);

    private final String path;
    private final String label;
    private final GameContext context;
    private final boolean selfOverlapping;

    SoundEffect(final String path, final String label, final GameContext context) {
        this(path, label, context, true);
    }

    SoundEffect(final String path, final String label, final GameContext context, final boolean selfOverlapping) {
        this.path = path;
        this.label = label;
        this.context = context;
        this.selfOverlapping = selfOverlapping;
    }
}
