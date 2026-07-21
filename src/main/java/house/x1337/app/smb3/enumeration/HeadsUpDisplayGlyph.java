package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * All available hud-font glyphs, mapped to their PNG filenames.
 */
@RequiredArgsConstructor
public enum HeadsUpDisplayGlyph {
    DIGIT_0("0"),
    DIGIT_1("1"),
    DIGIT_2("2"),
    DIGIT_3("3"),
    DIGIT_4("4"),
    DIGIT_5("5"),
    DIGIT_6("6"),
    DIGIT_7("7"),
    DIGIT_8("8"),
    DIGIT_9("9"),
    ARROW_LIT("arrow_lit"),
    ARROW_DARK("arrow_dark"),
    P_LEFT_DARK("p_left_dark"),
    P_RIGHT_DARK("p_right_dark"),
    P_LEFT_LIT("p_left_lit"),
    P_RIGHT_LIT("p_right_lit"),
    COIN("coin"),
    CLOCK("clock"),
    TIMES("times"),
    DOLLAR("dollar"),
    WORLD_0("world_0"),
    WORLD_1("world_1"),
    WORLD_2("world_2"),
    WORLD_3("world_3"),
    MARIO_LEFT("mario_left"),
    MARIO_RIGHT("mario_right"),
    LUIGI_LEFT("luigi_left"),
    LUIGI_RIGHT("luigi_right");

    @Getter
    private final String filename;

    public static HeadsUpDisplayGlyph[] getDigits() {
        return Arrays
            .stream(HeadsUpDisplayGlyph.values())
            .filter(headsUpDisplayGlyph -> headsUpDisplayGlyph.name().startsWith("DIGIT_"))
            .toArray(HeadsUpDisplayGlyph[]::new);
    }
}
