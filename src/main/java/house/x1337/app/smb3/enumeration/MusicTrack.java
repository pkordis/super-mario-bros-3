package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.sound.SoundTrack;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.GameContext.MAP;

/**
 * Every music track the game can play: the songs, which loop, and the short jingles, which play once. Each
 * constant holds the classpath asset path of its {@code .ogg} file, the label the editor shows for it, whether it
 * loops, and the {@link GameContext} it belongs to: the world map's songs ({@code WORLD_*}) to {@link GameContext#MAP},
 * everything else to {@link GameContext#LEVEL_SCENE}.
 *
 * <p>Level scenes store the constant's name, so renaming or removing a constant breaks the scenes that use it.
 *
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#play(MusicTrack)
 */
@Getter
@RequiredArgsConstructor
public enum MusicTrack implements SoundTrack {
    AIRSHIP("sound/music/airship.ogg", "Airship", true, LEVEL_SCENE),
    ATHLETIC("sound/music/athletic.ogg", "Athletic", true, LEVEL_SCENE),
    BATTLE_LOST("sound/music/battle_lost.ogg", "Battle Lost (jingle)", false, LEVEL_SCENE),
    BOWSER("sound/music/bowser.ogg", "Bowser Battle", true, LEVEL_SCENE),
    COIN_HEAVEN("sound/music/coin_heaven.ogg", "Coin Heaven", true, LEVEL_SCENE),
    COURSE_CLEAR("sound/music/course_clear.ogg", "Course Clear (jingle)", false, LEVEL_SCENE),
    CURSED_KINGS("sound/music/cursed_kings.ogg", "Cursed King's Room", true, LEVEL_SCENE),
    ENDING("sound/music/ending.ogg", "Ending", true, LEVEL_SCENE),
    FORTRESS("sound/music/fortress.ogg", "Fortress", true, LEVEL_SCENE),
    GAME_OVER("sound/music/game_over.ogg", "Game Over (jingle)", false, LEVEL_SCENE),
    HAMMER_BROS("sound/music/hammer_bros.ogg", "Hammer Bros. Battle", true, LEVEL_SCENE),
    HURRY_UP("sound/music/hurry_up.ogg", "Hurry Up! (jingle)", false, LEVEL_SCENE),
    KING_RESTORED("sound/music/king_restored.ogg", "King Restored (jingle)", false, LEVEL_SCENE),
    KOOPA_KIDS("sound/music/koopa_kids.ogg", "Koopaling Battle", true, LEVEL_SCENE),
    MUSIC_BOX("sound/music/music_box.ogg", "Music Box", true, LEVEL_SCENE),
    N_SPADE("sound/music/n_spade.ogg", "N-Spade Bonus Game", true, LEVEL_SCENE),
    OVERWORLD("sound/music/overworld.ogg", "Overworld", true, LEVEL_SCENE),
    PLAYER_DOWN("sound/music/player_down.ogg", "Player Down (jingle)", false, LEVEL_SCENE),
    P_SWITCH("sound/music/p_switch.ogg", "P-Switch", true, LEVEL_SCENE),
    STAR("sound/music/star.ogg", "Starman", true, LEVEL_SCENE),
    UNDERGROUND("sound/music/underground.ogg", "Underground", true, LEVEL_SCENE),
    VICTORY_FANFARE("sound/music/victory_fanfare.ogg", "Victory Fanfare (jingle)", false, LEVEL_SCENE),
    WARP_ZONE("sound/music/warp_zone.ogg", "Warp Zone", true, LEVEL_SCENE),
    WATERWORLD("sound/music/waterworld.ogg", "Underwater", true, LEVEL_SCENE),
    WORLD_1_GRASS_LAND("sound/music/grass_land.ogg", "World 1 Map: Grass Land", true, MAP),
    WORLD_2_DESERT_LAND("sound/music/desert_land.ogg", "World 2 Map: Desert Land", true, MAP),
    WORLD_3_WATER_LAND("sound/music/water_land.ogg", "World 3 Map: Water Land", true, MAP),
    WORLD_4_GIANT_LAND("sound/music/giant_land.ogg", "World 4 Map: Giant Land", true, MAP),
    WORLD_5_SKY_LAND("sound/music/sky_land.ogg", "World 5 Map: Sky Land", true, MAP),
    WORLD_6_ICE_LAND("sound/music/ice_land.ogg", "World 6 Map: Ice Land", true, MAP),
    WORLD_7_PIPE_LAND("sound/music/pipe_land.ogg", "World 7 Map: Pipe Land", true, MAP),
    WORLD_8_DARK_LAND("sound/music/dark_land.ogg", "World 8 Map: Dark Land", true, MAP);

    private final String path;
    private final String label;
    private final boolean looping;
    private final GameContext context;
}
