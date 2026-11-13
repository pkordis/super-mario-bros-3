package house.x1337.app.smb3.game.level.completion;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * How far the fireworks have faded in, step by step from the middle out: {@link #getStep()} is the step's place in
 * the fade, 1 first, whatever order the constants are declared in. Each step is a grid of 16 x 16 px blocks read
 * from its text file: {@code 'b'} bright, {@code 'd'} dim, {@code '.'} not seen yet. {@link #isBright} and
 * {@link #isSeen} take a cell of the fireworks' 16 x 16 cell area, two cells to a block.
 */
@Getter
public enum FireworksFade implements CharacterGridResource {
    ALL_BRIGHT(5, "classpath:/sprites/object/card/fireworks/fade/all_bright.txt"),
    ALMOST_ALL_BRIGHT(4, "classpath:/sprites/object/card/fireworks/fade/almost_all_bright.txt"),
    INNER_BRIGHT(3, "classpath:/sprites/object/card/fireworks/fade/inner_bright.txt"),
    MIDDLE(1, "classpath:/sprites/object/card/fireworks/fade/middle.txt"),
    MIDDLE_BRIGHT(2, "classpath:/sprites/object/card/fireworks/fade/middle_bright.txt");

    static final int BLOCKS = 8;
    static final int BLOCK_CELLS = 2;
    static final String BLOCK_CHARACTERS = "bd.";

    private final int step;
    private final String path;
    @Getter(AccessLevel.NONE)
    private final String[] blocks;

    FireworksFade(
        final int step,
        final String path
    ) {
        this.step = step;
        this.path = path;
        this.blocks = readGrid(path, BLOCKS, BLOCK_CHARACTERS);
    }

    public boolean isBright(
        final int column,
        final int row
    ) {
        return blockAt(column, row) == 'b';
    }

    public boolean isSeen(
        final int column,
        final int row
    ) {
        return blockAt(column, row) != '.';
    }

    private char blockAt(
        final int column,
        final int row
    ) {
        return blocks[row / BLOCK_CELLS].charAt(column / BLOCK_CELLS);
    }
}
