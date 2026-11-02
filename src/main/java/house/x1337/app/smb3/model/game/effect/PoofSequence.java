package house.x1337.app.smb3.model.game.effect;

import java.util.List;

import static java.lang.Math.clamp;

/**
 * The frame-selection policy shared by SMB3's three "poof" cloud effects.
 *
 * <p>All three draw the same four sprites. Two of them index the same four-entry pattern table, which
 * the ROM happens to store twice, and the third has a table of its own in a different order.
 *
 * @param ticksPerFrame     how many ticks each frame is held
 * @param cycling           {@code true} to cycle the four frames, {@code false} to clamp at the ends
 * @param flipPeriodTicks   the counter bit that sets the vertical flip
 * @param frameByStep       the shared frame shown for each step of the ROM's own pattern table
 * @param startTicks        the counter a cloud drawn with this policy starts from
 * @param lowestDrawnTicks  the lowest counter at which the cloud is drawn
 * @param highestDrawnTicks the highest counter at which the cloud is drawn
 */
public record PoofSequence(
    int ticksPerFrame,
    boolean cycling,
    int flipPeriodTicks,
    List<Integer> frameByStep,
    int startTicks,
    int lowestDrawnTicks,
    int highestDrawnTicks
) {
    public static final String POOF_FRAMES_CONTEXT = "sprites/effect/poof/";
    private static final List<Integer> POOF_DEATH_ORDER = List.of(0, 1, 3, 2);
    private static final List<Integer> TABLE_ORDER = List.of(0, 1, 2, 3);
    private static final int FLIP_PERIOD_TICKS = 4;
    private static final int POOF_DEATH_FLIP_PERIOD_TICKS = 2;
    private static final int POOF_DEATH_HIGHEST_DRAWN_TICKS = 30;
    public static final int FRAME_COUNT = 4;
    public static final int ONE_SHOT_TICKS = 32;
    public static final int POOF_DEATH_TICKS = 32;

    /**
     * The switch-block puff: 4 frames of 8 ticks, played once and read descending.
     *
     * @return a one-shot sequence
     */
    public static PoofSequence oneShot() {
        return new PoofSequence(
            ONE_SHOT_TICKS / FRAME_COUNT,
            false,
            FLIP_PERIOD_TICKS,
            TABLE_ORDER,
            ONE_SHOT_TICKS,
            0,
            ONE_SHOT_TICKS
        );
    }

    /**
     * The suit-change puff: 4 frames of 4 ticks, cycling.
     *
     * @return a wrapping sequence
     */
    public static PoofSequence wrapping() {
        return new PoofSequence(FLIP_PERIOD_TICKS, true, FLIP_PERIOD_TICKS, TABLE_ORDER, 0, 0, Integer.MAX_VALUE);
    }

    /**
     * The enemy poof death: 4 frames of 8 ticks in {@code PoofDeath_Pats} order, played once.
     *
     * @return a poof-death sequence
     */
    public static PoofSequence poofDeath() {
        return new PoofSequence(
            ONE_SHOT_TICKS / FRAME_COUNT,
            false,
            POOF_DEATH_FLIP_PERIOD_TICKS,
            POOF_DEATH_ORDER,
            POOF_DEATH_TICKS,
            1,
            POOF_DEATH_HIGHEST_DRAWN_TICKS
        );
    }

    /**
     * Resolves the frame index for a countdown value.
     *
     * @param counter the effect's remaining-ticks counter
     * @return an index into the shared frame set, always within {@code 0..FRAME_COUNT-1}
     */
    public int frameIndexFor(final int counter) {
        final int step = counter / ticksPerFrame;
        final int tableIndex = cycling
            ? step & (FRAME_COUNT - 1)
            : clamp(step, 0, FRAME_COUNT - 1);
        return frameByStep.get(tableIndex);
    }

    /**
     * Whether the cloud is drawn vertically flipped on this tick.
     *
     * @param counter the effect's remaining-ticks counter
     * @return {@code true} when the flipped state is showing
     */
    public boolean isFlippedVerticallyAt(final int counter) {
        return (counter & flipPeriodTicks) != 0;
    }

    /**
     * @param counter the effect's remaining-ticks counter
     * @return {@code true} if the cloud is on screen at this counter
     */
    public boolean isDrawnAt(final int counter) {
        return counter >= lowestDrawnTicks && counter <= highestDrawnTicks;
    }
}
