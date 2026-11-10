package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Every music track the game can play: the songs, which loop, and the short jingles. Each constant holds the
 * classpath asset path of its {@code .ogg} file.
 *
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#play(MusicTrack)
 */
@Getter
@RequiredArgsConstructor
public enum MusicTrack {
    AIRSHIP("sound/music/airship.ogg"),
    ATHLETIC("sound/music/athletic.ogg"),
    BATTLE_LOST("sound/music/battle_lost.ogg"),
    BOWSER("sound/music/bowser.ogg"),
    COIN_HEAVEN("sound/music/coin_heaven.ogg"),
    COURSE_CLEAR("sound/music/course_clear.ogg"),
    CURSED_KINGS("sound/music/cursed_kings.ogg"),
    DARK_LAND("sound/music/dark_land.ogg"),
    DESERT_LAND("sound/music/desert_land.ogg"),
    ENDING("sound/music/ending.ogg"),
    FORTRESS("sound/music/fortress.ogg"),
    GAME_OVER("sound/music/game_over.ogg"),
    GIANT_LAND("sound/music/giant_land.ogg"),
    GRASS_LAND("sound/music/grass_land.ogg"),
    HAMMER_BROS("sound/music/hammer_bros.ogg"),
    HURRY_UP("sound/music/hurry_up.ogg"),
    ICE_LAND("sound/music/ice_land.ogg"),
    KING_RESTORED("sound/music/king_restored.ogg"),
    KOOPA_KIDS("sound/music/koopa_kids.ogg"),
    MUSIC_BOX("sound/music/music_box.ogg"),
    N_SPADE("sound/music/n_spade.ogg"),
    OVERWORLD("sound/music/overworld.ogg"),
    PIPE_LAND("sound/music/pipe_land.ogg"),
    PLAYER_DOWN("sound/music/player_down.ogg"),
    P_SWITCH("sound/music/p_switch.ogg"),
    SKY_LAND("sound/music/sky_land.ogg"),
    STAR("sound/music/star.ogg"),
    UNDERGROUND("sound/music/underground.ogg"),
    VICTORY_FANFARE("sound/music/victory_fanfare.ogg"),
    WARP_ZONE("sound/music/warp_zone.ogg"),
    WATERWORLD("sound/music/waterworld.ogg"),
    WATER_LAND("sound/music/water_land.ogg");

    private final String path;
}
