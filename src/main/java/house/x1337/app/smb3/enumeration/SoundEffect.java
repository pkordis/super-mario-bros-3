package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Every sound effect the game can play. Each constant holds the classpath asset path of its {@code .ogg} file.
 *
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#play(SoundEffect)
 */
@Getter
@RequiredArgsConstructor
public enum SoundEffect {
    AIRSHIP_MOVEMENT("sound/effects/airship_movement.ogg"),
    BLOCK("sound/effects/block.ogg"),
    BOWSER_FIREBALL("sound/effects/bowser_fireball.ogg"),
    BREAKING_BLOCK("sound/effects/breaking_block.ogg"),
    CANNONBALL("sound/effects/cannonball.ogg"),
    CASINO_BEEP("sound/effects/casino_beep.ogg"),
    COIN("sound/effects/coin.ogg"),
    ENEMY_STOMP("sound/effects/enemy_stomp.ogg"),
    FIREBALL("sound/effects/fireball.ogg"),
    HAMMER_BRO_MOVEMENT("sound/effects/hammer_bro_movement.ogg"),
    JUMP("sound/effects/jump.ogg"),
    KOOPA_SHELL("sound/effects/koopa_shell.ogg"),
    LEVEL_CLEAR("sound/effects/level_clear.ogg"),
    LEVEL_START("sound/effects/level_start.ogg"),
    MOVE_CURSOR("sound/effects/move_cursor.ogg"),
    N_SPADE_CLEAR("sound/effects/n_spade_clear.ogg"),
    ONE_UP("sound/effects/one_up.ogg"),
    PAUSE("sound/effects/pause.ogg"),
    PLAYER_SPIN("sound/effects/player_spin.ogg"),
    POWER_DOWN("sound/effects/power_down.ogg"),
    POWER_UP("sound/effects/power_up.ogg"),
    POWER_UP_BLOCK("sound/effects/power_up_block.ogg"),
    SUPER_JUMP("sound/effects/super_jump.ogg"),
    TANOOKI("sound/effects/tanooki.ogg"),
    TWIRL("sound/effects/twirl.ogg"),
    VINE("sound/effects/vine.ogg"),
    WORLD_START("sound/effects/world_start.ogg"),
    WRONG("sound/effects/wrong.ogg");

    private final String path;
}
