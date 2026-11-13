package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * The characters of the bold font: thick white capitals, one 8 x 8 cell each, transparent around the letter, in
 * {@code font/bold/<name in lower case>.png}.
 *
 * <p>The font only has the characters listed here, so only texts made of them can be written with it.
 */
@Getter
@RequiredArgsConstructor
public enum BoldFontGlyph {
    A('A'),
    C('C'),
    D('D'),
    E('E'),
    G('G'),
    L('L'),
    O('O'),
    R('R'),
    S('S'),
    T('T'),
    U('U'),
    Y('Y'),
    EXCLAMATION('!'),
    SPACE(' ');

    private final char character;

    public String getFilename() {
        return name().toLowerCase(Locale.ROOT);
    }
}
