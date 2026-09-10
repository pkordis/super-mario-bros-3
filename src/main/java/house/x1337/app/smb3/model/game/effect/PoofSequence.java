package house.x1337.app.smb3.model.game.effect;

import static java.lang.Math.clamp;

/**
 * The frame-selection policy shared by SMB3's two "poof" cloud effects.
 *
 * <p>Both effects draw the same four sprites and index the same four-entry pattern table, which the
 * ROM happens to store twice:
 *
 * <pre>
 * prg007.asm:5247  Poof_Patterns:          .byte $47, $45, $43, $41   (SObj_Poof)
 * prg029.asm:2262  SuitLost_Poof_Patterns: .byte $47, $45, $43, $41   (Player_SuitLost_DoPoof)
 * </pre>
 *
 * <p>The shared sprite set at {@code sprites/effect/poof/frame_{0,3}.png} is stored in that <b>table
 * order</b> — {@code frame_0} = $47 (small dust) through {@code frame_3} = $41 (big puff) — so each
 * effect's own index arithmetic maps onto it directly:
 *
 * <ul>
 *   <li><b>{@link #oneShot}</b> — the switch-block puff. {@code SObj_Poof} seeds
 *       {@code SpecialObj_Data} with $20, decrements it once per tick and picks the pattern with
 *       {@code data >> 3}: four frames of 8 ticks, read <em>descending</em> (big puff first, fading to
 *       dust), then the object is removed.</li>
 *   <li><b>{@link #wrapping}</b> — the suit-change puff. {@code Player_SuitLost_DoPoof} picks with
 *       {@code (Player_SuitLost & $0C) >> 2}: four frames of 4 ticks that <em>cycle</em>, so over the
 *       $17-tick suit change the sequence repeats.</li>
 * </ul>
 *
 * <p>What differs beyond this policy — where the cloud is anchored, and how it is drawn — is
 * deliberately left to each call site: the switch puff is a one-shot at a fixed level cell with its
 * own geometry, while the suit puff replaces the player's own sprite and follows them.
 *
 * @param ticksPerFrame how many ticks each frame is held
 * @param cycling       {@code true} to cycle the four frames, {@code false} to clamp at the ends
 */
public record PoofSequence(int ticksPerFrame, boolean cycling) {
    /** Classpath directory holding the shared poof frames, in ROM table order. */
    public static final String POOF_FRAMES_CONTEXT = "sprites/effect/poof/";

    /** Number of poof sprites, i.e. the length of the ROM's pattern table. */
    public static final int FRAME_COUNT = 4;

    /**
     * How often the ROM toggles the cloud's vertical flip: {@code SObj_Poof} derives it from
     * {@code Level_NoStopCnt} with {@code LSR A x3 / ROR A / AND #SPR_VFLIP}, which keeps bit 2 — so it
     * alternates every 4 ticks.
     */
    private static final int FLIP_PERIOD_TICKS = 4;

    /** Total lifetime of the switch-block puff: {@code LATP_PSwitch} seeds the counter with $20. */
    public static final int ONE_SHOT_TICKS = 0x20;

    /**
     * The switch-block puff: 4 frames of 8 ticks, played once and read descending
     * ({@code SObj_Poof}: {@code SpecialObj_Data >> 3}).
     *
     * @return a one-shot sequence
     */
    public static PoofSequence oneShot() {
        return new PoofSequence(ONE_SHOT_TICKS / FRAME_COUNT, false);
    }

    /**
     * The suit-change puff: 4 frames of 4 ticks, cycling
     * ({@code Player_SuitLost_DoPoof}: {@code (counter & $0C) >> 2}).
     *
     * @return a wrapping sequence
     */
    public static PoofSequence wrapping() {
        return new PoofSequence(FLIP_PERIOD_TICKS, true);
    }

    /**
     * Resolves the frame index for a countdown value.
     *
     * @param counter the effect's remaining-ticks counter
     * @return an index into the shared frame set, always within {@code 0..FRAME_COUNT-1}
     */
    public int frameIndexFor(final int counter) {
        final int step = counter / ticksPerFrame;
        return cycling
            ? step & (FRAME_COUNT - 1)
            : clamp(step, 0, FRAME_COUNT - 1);
    }

    /**
     * Whether the cloud is drawn vertically flipped on this tick.
     *
     * <p>The ROM draws the 16x16 puff as two 8x16 sprites whose attributes differ by
     * {@code EOR (SPR_HFLIP | SPR_VFLIP)}, and toggles the left half's V-flip every 4 ticks. Those two
     * states are exact vertical mirrors of one another, so flipping the assembled tile reproduces the
     * original rather than approximating it.
     *
     * @param counter the effect's remaining-ticks counter
     * @return {@code true} when the flipped state is showing
     */
    public boolean isFlippedVerticallyAt(final int counter) {
        return (counter & FLIP_PERIOD_TICKS) != 0;
    }
}
