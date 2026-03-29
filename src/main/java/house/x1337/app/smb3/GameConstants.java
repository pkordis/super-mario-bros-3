package house.x1337.app.smb3;

import com.jme3.math.ColorRGBA;
import house.x1337.app.smb3.enumeration.TileType;
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
     * Logical tile size used throughout game-world maths (collision, camera, layout).
     * Equals {@code TILE_SPRITE_SIZE × TILE_SCALE} = 64 px.
     */
    public static final int TILE_SIZE = TILE_SPRITE_SIZE * TILE_SCALE;

    // -------------------------------------------------------------------------
    // Viewport
    // -------------------------------------------------------------------------

    public static final int VIEWPORT_WIDTH    = 1024;
    public static final int VIEWPORT_HEIGHT   = 960;
    public static final int VIEWPORT_TILES_X  = VIEWPORT_WIDTH  / TILE_SIZE;
    public static final int VIEWPORT_TILES_Y  = VIEWPORT_HEIGHT / TILE_SIZE;

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------

    // Frustum = VIEWPORT_TILES_Y / 2  →  visible height = VIEWPORT_TILES_Y game-units
    // This makes every sprite pixel map to exactly TILE_SCALE (4) screen pixels,
    // preventing sub-pixel texture sampling which causes tile shimmer during scrolling.
    // With frustum = 8.0 the ratio was 3.75 px/sprite-px (non-integer → constant shimmer).
    public static final float FRUSTUM = VIEWPORT_TILES_Y / 2.0F;

    // -------------------------------------------------------------------------
    // Player
    // -------------------------------------------------------------------------

    public static final int PLAYER_WIDTH  = 48;
    public static final int PLAYER_HEIGHT = 112;

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    public static final int TARGET_FPS = 60;
    public static final double FRAME_TIME_SECONDS = 1.0 / TARGET_FPS;

    public static final Tile NULL_TILE = Tile
        .builder()
        .id(TileType.NULL.ordinal())
        .type(TileType.NULL)
        .argbData(new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE])
        .build();

    public static final ColorRGBA BLACK = new ColorRGBA(0, 0, 0, 0);
}
