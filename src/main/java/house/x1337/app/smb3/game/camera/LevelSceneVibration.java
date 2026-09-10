package house.x1337.app.smb3.game.camera;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * The level-wide screen shake, porting SMB3's {@code Level_Vibration}
 * (dasm {@code smb3.asm:2678} "While greater than zero, screen vibrates (from impact of heavy
 * fellow)", driven by {@code prg008.asm Player_DoVibration} @ ~:950).
 *
 * <p>The ROM's routine, verbatim:
 *
 * <pre>
 * VibrationOffset:                        ; prg008.asm ~:945
 *     .byte 0,  2,  3,  1                 ; used while Vert_Scroll  &lt; $80
 *     .byte 0, -2, -3, -1                 ; used while Vert_Scroll &gt;= $80
 * Player_DoVibration:
 *     LDA Level_Vibration / BEQ done
 *     DEC Level_Vibration
 *     AND #$03                            ; index = PRE-decrement count &amp; 3
 *     LDY &lt;Vert_Scroll / BPL + / ORA #$04
 * +   TAY / LDA VibrationOffset,Y / PHA
 *     ADD Level_VertScroll / STA Level_VertScroll
 *     PLA
 * done:
 *     STA Vert_Scroll_Off
 * </pre>
 *
 * <p>Three consequences drive this port:
 *
 * <ul>
 *   <li><b>Vertical only, whole pixels, amplitude ≤ 3.</b> There is no horizontal component.</li>
 *   <li><b>The visible sequence is {@code 0, 1, 3, 2}</b>, not the table's declaration order:
 *       {@code DEC} touches memory, so {@code A} still holds the pre-decrement count when
 *       {@code AND #$03} runs. With the count running {@code $10..$01} the indices go
 *       {@code 0, 3, 2, 1}, so a {@link #powerShake()} plays {@code 0, 1, 3, 2} exactly four
 *       times.</li>
 *   <li><b>It bypasses the scroll limits.</b> The offset lands in {@code Vert_Scroll_Off}, the raw
 *       per-frame scroll offset written after {@code Player_DoScrolling} has already applied its
 *       rules and clamps. That is why the shake is visible even in a horizontal level whose view is
 *       locked at the bottom, and why this offset must be added <em>outside</em> the camera's
 *       level-bounds clamp (see {@code CameraState.update}).</li>
 * </ul>
 *
 * <p>The second half of {@code VibrationOffset} only flips the sign according to which half of the
 * nametable {@code Vert_Scroll} currently addresses — a PPU artefact with no gameplay meaning, so a
 * single sign is ported.
 *
 * <p>State is held for one whole simulation tick and deliberately <b>not</b> interpolated between
 * ticks the way {@link LevelSceneVerticalScroll#interpolate(double)} is: the effect <em>is</em> a
 * per-frame 1-to-3&nbsp;pixel jitter, and easing it across render frames would smear it away.
 *
 * <p>This is a shared primitive, not a P-Switch detail: besides the P-Switch stomp
 * ({@code prg008.asm:4767}) the ROM sets {@code Level_Vibration} for the heavy Koopalings' body
 * slams and other impacts ({@code prg001.asm:4569}, {@code :5335}, {@code :6140},
 * {@code prg004.asm:1354}, {@code :2205}, {@code prg005.asm:4644}, {@code :6226}). New callers get
 * their own named preset alongside {@link #powerShake()}.
 */
@Slf4j
@Singleton
public final class LevelSceneVibration {
    /**
     * dasm {@code VibrationOffset} in its declared order, since the descending {@code count & 3}
     * index below is the ROM's. It is that pairing — indices {@code 0, 3, 2, 1} into
     * {@code 0, 2, 3, 1} — that produces the visible {@code 0, 1, 3, 2} cycle; reordering the table
     * to playback order would break the index.
     */
    private static final int[] VIBRATION_OFFSETS = {0, 2, 3, 1};

    /** dasm {@code prg008.asm:4767}: {@code LDA #$10 / STA Level_Vibration} on the P-Switch stomp. */
    private static final int POWER_SHAKE_FRAMES = 0x10;

    private int framesRemaining;
    private int verticalOffsetPixels;

    /**
     * Starts the "something heavy just landed" shake: 16 frames of vertical jitter
     * (dasm {@code Level_Vibration = $10}). Restarting an in-flight shake reloads the counter, as
     * the ROM's plain {@code STA} does.
     */
    public void powerShake() {
        vibrate(POWER_SHAKE_FRAMES);
    }

    /**
     * Advances the shake by one simulation tick: latches this tick's offset, then counts down —
     * the order {@code Player_DoVibration} uses, so the offset for a given frame is derived from
     * the count as it stood at the start of that frame.
     *
     * <p>Call once per 60&nbsp;Hz tick, after the players and objects have updated, mirroring the
     * ROM's chain where {@code Player_DoSpecialTiles} (which presses the switch) runs immediately
     * before {@code Player_DoVibration} (prg008.asm ~:934-935). Consequently the press frame itself
     * always shows offset 0 — {@code $10 & 3 == 0}.
     */
    public void tick() {
        if (framesRemaining == 0) {
            verticalOffsetPixels = 0;
            return;
        }
        verticalOffsetPixels = VIBRATION_OFFSETS[framesRemaining & 0x03];
        framesRemaining--;
    }

    /**
     * @return this tick's shake displacement in NES pixels, {@code 0..3}
     */
    public int getVerticalOffsetPixels() {
        return verticalOffsetPixels;
    }

    /**
     * The same displacement expressed for the camera. The ROM adds the offset to
     * {@code Level_VertScroll}, which walks the view <em>down</em> the level, whereas the camera
     * lives in Y-up game units — hence the negation — and one NES pixel is
     * {@code 1 / TILE_SPRITE_SIZE} of a game unit.
     *
     * @return the camera's vertical offset for this tick, in game units
     */
    public double getCameraOffsetUnits() {
        return -verticalOffsetPixels / (double) TILE_SPRITE_SIZE;
    }

    /**
     * @return whether a shake is still running
     */
    public boolean isVibrating() {
        return framesRemaining > 0;
    }

    /**
     * Clears the shake. Called when a level is set up so a shake left running by a previous level
     * (or a previous run of the same level in the editor's tester) cannot leak into the new one —
     * this bean is a singleton, unlike the per-scene {@link LevelSceneVerticalScroll}.
     */
    public void reset() {
        framesRemaining = 0;
        verticalOffsetPixels = 0;
    }

    private void vibrate(final int frames) {
        framesRemaining = frames;
        verticalOffsetPixels = 0;
        log.debug("Level vibration started for {} frames", frames);
    }
}
