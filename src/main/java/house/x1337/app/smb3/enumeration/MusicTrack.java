package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.sound.SoundTrack;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.GameContext.MAP;

/**
 * Every music track the game can play: the songs, which loop, and the short jingles, which play once. Each
 * constant holds the classpath asset path of its {@code .ogg} file, the label the editor shows for it, whether it
 * loops, whether it has a faster version, and the {@link GameContext} it belongs to: the world map's songs
 * ({@code WORLD_*}) and the warp zone to {@link GameContext#MAP}, everything else to {@link GameContext#LEVEL_SCENE}.
 *
 * <p>A track's faster version, played once the level is hurried, is the file of the same name under
 * {@value #FASTER_DIRECTORY}. See {@code kb/sound.md}, "Faster music after Hurry Up".
 *
 * <p>Level scenes store the constant's name, so renaming or removing a constant breaks the scenes that use it.
 *
 * @see house.x1337.app.smb3.game.sound.SoundPlayer#play(MusicTrack)
 */
@Getter
@RequiredArgsConstructor
public enum MusicTrack implements SoundTrack {
    AIRSHIP("sound/music/airship.ogg", "Airship", true, true, LEVEL_SCENE),
    ATHLETIC("sound/music/athletic.ogg", "Athletic", true, true, LEVEL_SCENE),
    BATTLE_LOST("sound/music/battle_lost.ogg", "Battle Lost (jingle)", false, false, LEVEL_SCENE),
    BOWSER("sound/music/bowser.ogg", "Bowser Battle", true, true, LEVEL_SCENE),
    COIN_HEAVEN("sound/music/coin_heaven.ogg", "Coin Heaven", true, true, LEVEL_SCENE),
    COURSE_CLEAR("sound/music/course_clear.ogg", "Course Clear (jingle)", false, false, LEVEL_SCENE),
    CURSED_KINGS("sound/music/cursed_kings.ogg", "Cursed King's Room", true, false, LEVEL_SCENE),
    ENDING("sound/music/ending.ogg", "Ending", true, false, LEVEL_SCENE),
    FORTRESS("sound/music/fortress.ogg", "Fortress", true, true, LEVEL_SCENE),
    GAME_OVER("sound/music/game_over.ogg", "Game Over (jingle)", false, false, LEVEL_SCENE),
    HAMMER_BROS("sound/music/hammer_bros.ogg", "Hammer Bros. Battle", true, true, LEVEL_SCENE),
    HURRY_UP("sound/music/hurry_up.ogg", "Hurry Up! (jingle)", false, false, LEVEL_SCENE),
    KING_RESTORED("sound/music/king_restored.ogg", "King Restored (jingle)", false, false, LEVEL_SCENE),
    KOOPA_KIDS("sound/music/koopa_kids.ogg", "Koopaling Battle", true, true, LEVEL_SCENE),
    MUSIC_BOX("sound/music/music_box.ogg", "Music Box", true, false, LEVEL_SCENE),
    N_SPADE("sound/music/n_spade.ogg", "N-Spade Bonus Game", true, false, LEVEL_SCENE),
    OVERWORLD("sound/music/overworld.ogg", "Overworld", true, true, LEVEL_SCENE),
    PLAYER_DOWN("sound/music/player_down.ogg", "Player Down (jingle)", false, false, LEVEL_SCENE),
    P_SWITCH("sound/music/p_switch.ogg", "P-Switch", true, true, LEVEL_SCENE),
    STAR("sound/music/star.ogg", "Starman", true, true, LEVEL_SCENE),
    UNDERGROUND("sound/music/underground.ogg", "Underground", true, true, LEVEL_SCENE),
    VICTORY_FANFARE("sound/music/victory_fanfare.ogg", "Victory Fanfare (jingle)", false, false, LEVEL_SCENE),
    WARP_ZONE("sound/music/warp_zone.ogg", "Warp Zone", true, false, MAP),
    WATERWORLD("sound/music/waterworld.ogg", "Underwater", true, true, LEVEL_SCENE),
    WORLD_1_GRASS_LAND("sound/music/grass_land.ogg", "World 1 Map: Grass Land", true, false, MAP),
    WORLD_2_DESERT_LAND("sound/music/desert_land.ogg", "World 2 Map: Desert Land", true, false, MAP),
    WORLD_3_WATER_LAND("sound/music/water_land.ogg", "World 3 Map: Water Land", true, false, MAP),
    WORLD_4_GIANT_LAND("sound/music/giant_land.ogg", "World 4 Map: Giant Land", true, false, MAP),
    WORLD_5_SKY_LAND("sound/music/sky_land.ogg", "World 5 Map: Sky Land", true, false, MAP),
    WORLD_6_ICE_LAND("sound/music/ice_land.ogg", "World 6 Map: Ice Land", true, false, MAP),
    WORLD_7_PIPE_LAND("sound/music/pipe_land.ogg", "World 7 Map: Pipe Land", true, false, MAP),
    WORLD_8_DARK_LAND("sound/music/dark_land.ogg", "World 8 Map: Dark Land", true, false, MAP);

    private static final String FASTER_DIRECTORY = "sound/music/faster/";

    private final String path;
    private final String label;
    private final boolean looping;
    private final boolean faster;
    private final GameContext context;

    public String getFasterPath() {
        return FASTER_DIRECTORY + path.substring(path.lastIndexOf('/') + 1);
    }
}
