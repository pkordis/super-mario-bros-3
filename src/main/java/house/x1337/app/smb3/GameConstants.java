package house.x1337.app.smb3;

import com.jme3.math.ColorRGBA;
import house.x1337.app.smb3.display.MonitorRefreshRate;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.object.level.EmptyLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class GameConstants {

    // -------------------------------------------------------------------------
    // Tile sizing — two distinct spaces
    //
    //  ACTUAL  (sprite-space) : 16 × 16 px — the raw pixel grid stored in
    //                           Tile.spriteRgbData / Tile.SPRITE_SIDE_SIZE_PIXELS.
    //
    //  LOGICAL (game-space)   : 64 × 64 px — the size used for collision detection,
    //                           camera maths, scene layout and all on-screen rendering.
    //
    // The bridge between the two is TILE_SCALE (currently 4).
    // Every game-space measurement is TILE_SPRITE_SIZE × TILE_SCALE, so changing
    // TILE_SCALE in the future is the single knob needed to support different
    // zoom scenes without touching any geometry or physics code.
    // -------------------------------------------------------------------------

    /** Raw sprite dimensions in pixels. Must match {@code Tile.SPRITE_SIDE_SIZE_PIXELS}. */
    public static final int TILE_SPRITE_SIZE = 16;

    /** Integer scale factor: screen pixels rendered per sprite pixel. */
    public static final int TILE_SCALE = 4;


    /**
     * Sprite size: one full tile (16×16 sprite pixels).
     */
    public static final float TILE_SIZE_GAME_UNITS = 1.0f;


    public static final double FIXED_POINT_TO_GAME_UNITS = TILE_SIZE_GAME_UNITS / (TILE_SPRITE_SIZE * TILE_SPRITE_SIZE);

    public static final float PIXELS_TO_GAME_UNITS = TILE_SIZE_GAME_UNITS / TILE_SPRITE_SIZE;

    /**
     * Logical tile size used throughout game-world maths (collision, camera, layout).
     * Equals {@code TILE_SPRITE_SIZE × TILE_SCALE} = 64 px.
     */
    public static final int TILE_SIZE = TILE_SPRITE_SIZE * TILE_SCALE;

    // -------------------------------------------------------------------------
    // Viewport
    // -------------------------------------------------------------------------

    public static final int VIEWPORT_WIDTH = 1024;
    public static final int VIEWPORT_HEIGHT = 960;
    public static final int VIEWPORT_TILES_X = VIEWPORT_WIDTH / TILE_SIZE;
    public static final int VIEWPORT_TILES_Y = VIEWPORT_HEIGHT / TILE_SIZE;

    // -------------------------------------------------------------------------
    // HUD — fixed status bar at the bottom of the screen
    //
    // The NES original splits its 240 scanlines into 192 game + 48 status bar
    // (3 tiles × 16 px/tile). We mirror that ratio: the bottom 3 tile-rows are
    // reserved for the HUD and the upper 12 tile-rows are the scrollable game
    // area. The HUD is rendered in a separate viewport so it remains fixed
    // regardless of camera movement.
    // -------------------------------------------------------------------------

    /** Height of the HUD region in tile rows (3 tiles = 48 NES scanlines). */
    public static final int HUD_TILES_Y = 3;

    /** Height of the game area in tile rows (total viewport tiles minus HUD). */
    public static final int GAME_TILES_Y = VIEWPORT_TILES_Y - HUD_TILES_Y;

    /**
     * Normalized bottom edge of the game viewport (fraction of window height).
     * The HUD occupies [0, HUD_VIEWPORT_BOTTOM) and the game occupies
     * [HUD_VIEWPORT_BOTTOM, 1.0].
     */
    public static final float HUD_VIEWPORT_BOTTOM = (float) HUD_TILES_Y / VIEWPORT_TILES_Y;

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------

    // Frustum = GAME_TILES_Y / 2  →  visible height = GAME_TILES_Y game-units.
    // This makes every sprite pixel map to exactly TILE_SCALE (4) screen pixels,
    // preventing sub-pixel texture sampling which causes tile shimmer during scrolling.
    // The frustum covers only the game area (12 tiles), not the full window.
    public static final float FRUSTUM = GAME_TILES_Y / 2.0F;

    // -------------------------------------------------------------------------
    // Timing
    //
    // SIMULATION_FPS — the fixed rate at which game logic (physics, animation,
    //                  P-meter, timers) advances. Must remain 60 to match the
    //                  NES frame cadence that all per-tick constants assume.
    //
    // TARGET_FPS    — the rate at which frames are rendered and presented. Taken
    //                 from the display settings of the monitor rendering the game
    //                 so frames land on the monitor's own cadence; usually higher
    //                 than SIMULATION_FPS, in which case the game loop accumulates
    //                 real time and runs the correct number of simulation steps
    //                 per render frame.
    // -------------------------------------------------------------------------

    public static final int SIMULATION_FPS = 60;
    public static final double SIMULATION_DT = 1.0 / SIMULATION_FPS;

    /**
     * Render rate in Hz, read from the monitor that renders the game.
     * Falls back to {@link MonitorRefreshRate#FALLBACK_HERTZ} when the monitor
     * reports an unknown rate or no display is available.
     */
    public static final int TARGET_FPS = MonitorRefreshRate.ofDefaultScreen();
    public static final double FRAME_TIME_SECONDS = 1.0 / TARGET_FPS;

    public static final Tile NULL_TILE = Tile
        .builder()
        .id(TileType.NULL.ordinal())
        .type(TileType.NULL)
        .argbData(new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE])
        .build();

    public static final LevelObject EMPTY_LEVEL_OBJECT = new EmptyLevelObject();

    public static final ColorRGBA BLACK = new ColorRGBA(0, 0, 0, 0);

    public static final float Z_STEP_BETWEEN_LAYERS = 0.01f;
    // The poof cloud sits between the baked interactive-objects layer (order 4 -> 0.04, where the
    // switch block is painted) and the block bounce (0.05), so it covers the switch block it spawns
    // with while still passing behind the empty block bouncing out of the spawner.
    public static final float Z_DEPTH_POOF = 0.045f;
    public static final float Z_DEPTH_BRICK_BLOCK_BOUNCE = 0.05f;
    public static final float Z_DEPTH_ITEM_REWARD = 0.06f;
    // A Para-Goomba's wings are OAM slots 2 and 3 where its body is 0 and 1, and a lower slot wins on
    // the NES, so the wings pass behind the body. They still sit in front of the reward items.
    public static final float Z_DEPTH_ENEMY_WING = 0.065f;
    // A waking Koopa Troopa's feet are in the same two slots behind its shell (Sprite_RAM+$08 and +$0C
    // against the shell's +$00 and +$04), so they share the wings' depth for the same reason.
    public static final float Z_DEPTH_ENEMY_FOOT = Z_DEPTH_ENEMY_WING;
    // Enemies sit just in front of the reward items and just behind the player (PlayerVisibility
    // FOREGROUND = 0.1), so a Goomba walking over a rolling mushroom reads correctly while the player
    // still passes in front of both. The NES has no depth here at all - sprite order is OAM slot order.
    public static final float Z_DEPTH_ENEMY = 0.07f;

    /**
     * How far <b>behind</b> its holder an object in the player's hands is drawn, so the hands stay
     * visible around it.
     *
     * <p>This is the ROM's ordering, not a preference. Objects take their OAM slot from
     * {@code SprRamOffsets} (dasm prg000.asm:590), every entry of which is {@code $40} or higher, while
     * the player is rooted at {@code $28} ({@code prg008.asm:480}). A lower OAM index draws in front on
     * the NES, so the player is in front of every object, carried or not.
     *
     * <p>Added to the holder's own {@code PlayerVisibility.getPlayerZ()} rather than being an absolute
     * depth, so the carry survives the player switching foreground layer — an absolute value would put
     * the shell in front of terrain the player had just gone behind. Note the result still sits ahead of
     * {@link #Z_DEPTH_ENEMY}, which is also right: a carried shell passes in front of a walking Goomba.
     */
    public static final float Z_OFFSET_CARRIED_BEHIND_PLAYER = 0.006f;

    /**
     * The exception: while the camera-facing turn-around pose is running, the carried object is drawn
     * <b>in front</b> of its holder.
     *
     * <p>{@code ObjState_Held} forces {@code Object_SprRAM = $10} whenever {@code Player_PipeFace} is
     * non-zero (dasm prg000 @ PRG000_CF1A), and {@code $10} is below the player's {@code $28} — so for
     * those few ticks, and only those, the object wins. Which is what the pose is: the player turns to
     * face the viewer and holds the thing out towards them.
     *
     * <p>Numerically equal to {@link #Z_OFFSET_CARRIED_BEHIND_PLAYER} and deliberately named apart from
     * it: they are opposite sides of one comparison and either could be retuned alone.
     */
    public static final float Z_OFFSET_CARRIED_IN_FRONT_OF_PLAYER = 0.006f;

    /**
     * How far behind a carried object its own secondary quads sit — a waking shell's flickering feet,
     * which the ROM puts in the slots after the shell's ({@code Sprite_RAM+$08} and {@code +$0C} against
     * the shell's {@code +$00} and {@code +$04}, dasm prg000.asm:2398). Applied either side of the
     * comparison above, so the feet stay behind the shell whichever side of the player it is on.
     */
    public static final float Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT = 0.002f;

    public static final float Z_DEPTH_BRICK_BLOCK_FRAGMENT = 0.11f;

    // -------------------------------------------------------------------------
    // SMB3 velocity constants (4.4 fixed-point / 16 → px/frame from the JS port)
    // All values are in game-units (tile-fraction) per tick.
    // -------------------------------------------------------------------------

    public static final double PLAYER_TOPWALKSPEED = 1.5;
    public static final double PLAYER_TOPRUNSPEED = 2.5;
    public static final double PLAYER_TOPPOWERSPEED = 3.5;

    /**
     * Minimum velocity magnitude for the "spread-eagle" running sprites.
     * From prg008.asm {@code Player_SetSpecialFrames}: {@code CMP #$37}.
     * In 4.4 fixed-point: $37/16 = 3.4375 px/frame.
     */
    public static final double PLAYER_SPREAD_EAGLE_THRESHOLD = 3.4375;

    /**
     * Minimum velocity magnitude to trigger the skid state when the player
     * presses the opposite direction. From prg008.asm:
     * {@code LDA Player_XVel; ADD #$01; CMP #$03; BLT} — i.e. |XVel| ≥ $02.
     * In 4.4 fixed-point: $02/16 = 0.125 px/frame.
     */
    public static final double PLAYER_SKID_VEL_THRESHOLD = 0.125;
    public static final double PLAYER_FLY_YVEL = -1.5;
    public static final double PLAYER_FLY_APEX_YVEL = -1.0;
    public static final double PLAYER_TAILWAG_YVEL = 1.0;

    public static final double GRAVITY_SLOW = 1.0;
    public static final double GRAVITY_FAST = 5.0;
    public static final double[] JUMP_FORCE = {-3.5, -3.625, -3.75, -4.0};

    /**
     * Upward velocity the player is given for stomping an enemy (dasm prg000 {@code Player_HitEnemy}
     * @ PRG000_D2B4: {@code LDA #-$40 / STA Player_YVel}), i.e. {@code -$40/16} px/frame.
     *
     * <p>The ROM sets this one value whatever the player is doing; there is no separate boosted variant
     * for holding A, and a stomp grants no {@code Player_AllowAirJump} (only springboards and bolts do).
     * The extra height from holding A falls out of the ordinary variable-height gravity instead: while
     * the player is rising faster than {@code -$20} with A held, gravity is {@link #GRAVITY_SLOW} rather
     * than {@link #GRAVITY_FAST}. Because this bounce starts beyond that threshold, holding A stretches
     * it well past a normal jump, which is what lets a chain of stomps climb.
     */
    public static final double PLAYER_STOMP_BOUNCE_YVEL = -4.0;

    /**
     * Upward velocity the death throe launches with (dasm {@code Player_Die} @ prg000 PRG000_DA8E:
     * {@code LDA #-64 / STA Player_YVel}), i.e. {@code -$40/16} px/frame — the player pops up before
     * falling off the bottom of the screen. Numerically the same as {@link #PLAYER_STOMP_BOUNCE_YVEL},
     * but written by a different routine and free to be retuned.
     */
    public static final double PLAYER_DEATH_RISE_YVEL = -4.0;

    /**
     * Ticks the dead player holds still in place before the death throe launches (dasm {@code Player_Die}
     * @ prg000: {@code LDA #$30 / STA Event_Countdown}). While this counts down, the dying routine draws
     * the dead frame but applies no motion (prg029 {@code Player_Die_Dying} jumps to the draw-only
     * {@code PRG029_D771} whenever {@code Event_Countdown != 0}); only when it reaches zero does the
     * rise-and-fall ({@code PRG029_D768}) begin. {@code $30 = 48} ticks.
     */
    public static final int PLAYER_DEATH_PAUSE_TICKS = 0x30;

    /**
     * Per-tick gravity applied to the death throe (dasm prg029 PRG029_D768: {@code INC Player_YVel}
     * twice = {@code +2} in 4.4 fixed-point, i.e. {@code 2/16} px/frame²). Decelerates the rise to an
     * apex at ~32 ticks, then accelerates the fall.
     */
    public static final double PLAYER_DEATH_GRAVITY = 2.0 / TILE_SPRITE_SIZE;

    /**
     * Terminal fall speed of the death throe (dasm {@code Player_ApplyYVelocity} caps at
     * {@code FALLRATE_MAX = $40}, i.e. {@code $40/16} px/frame). The dying body never falls faster
     * than this on its way off-screen.
     */
    public static final double PLAYER_DEATH_FALL_MAX = 4.0;

    /**
     * Ticks a player who fell into a pit waits, unseen, before the level ends (dasm prg008 @ PRG008_A3FA,
     * lines 860-861: {@code LDA #$c0 / STA Event_Countdown}, counted down by {@code Player_Die_FellOff} in
     * prg029). {@code $C0 = 192} ticks is how long the death music plays, which is what the wait stands
     * for: {@code DeathMusicStandIn} counts it until a sound engine reports the real track's end.
     */
    public static final int PLAYER_PIT_DEATH_COUNTDOWN_TICKS = 192;

    public static final int PMETER_LEVELS = 7;
    public static final int PMETER_CHARGE_FRAMES = 8;
    public static final int PMETER_DRAIN_FRAMES = 24;
    public static final int PMETER_FULL_HOLD_FRAMES = 16;
    public static final int FLY_TIME = 0x80;
    public static final int WAG_COUNT = 0x10;
}
