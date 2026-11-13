package house.x1337.app.smb3.game.level.completion;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * The shapes of the fireworks the fanfare sets off, one per card face, each read from its text file: a dot in each
 * {@code '#'} cell, nothing in a {@code '.'} one.
 */
@Getter
public enum FireworksShape implements CharacterGridResource {
    MUSHROOM("classpath:/sprites/object/card/fireworks/shape/mushroom.txt"),
    FLOWER("classpath:/sprites/object/card/fireworks/shape/flower.txt"),
    STAR("classpath:/sprites/object/card/fireworks/shape/star.txt");

    static final int CELLS = 16;
    static final String CELL_CHARACTERS = "#.";

    private final String path;
    @Getter(AccessLevel.NONE)
    private final String[] rows;

    FireworksShape(final String path) {
        this.path = path;
        this.rows = readGrid(path, CELLS, CELL_CHARACTERS);
    }

    public boolean hasDot(
        final int column,
        final int row
    ) {
        return rows[row].charAt(column) == '#';
    }
}
