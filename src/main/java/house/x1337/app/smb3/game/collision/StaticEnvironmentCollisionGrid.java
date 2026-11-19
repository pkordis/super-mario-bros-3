package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.block.motion.BlockMotionManager;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.event.PowerSwitchExpired;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.collision.CollisionProbe;
import house.x1337.app.smb3.model.game.collision.DirectionalProbes;
import house.x1337.app.smb3.model.game.collision.ProbeLocation;
import house.x1337.app.smb3.model.game.collision.SlopeProfile;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.util.GameMath;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.OptionalInt;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.model.game.Coordinates.fromPlayerOffset;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_SLOPED_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_SLOPED_PROBES;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;

/**
 * Resolves the players against the scene's static terrain, and answers the terrain queries of everything else that
 * moves through it. How ramps are walked, and the limits below, are in {@code kb/slopes.md}.
 *
 * <p>On a ramp the player is stood on the surface under {@link #SLOPE_FEET_PROBE}: pulled up out of the ground by at
 * most {@value #SLOPE_CLIMB_LIMIT_PIXELS} pixels (a flat surface only by less than {@value #FLAT_SNAP_LIMIT_PIXELS}),
 * and, while grounded, pulled down onto ground at most {@value #SLOPE_STICK_PIXELS} pixels below. A player rising no
 * faster than {@value #SLOPE_LANDING_MIN_Y_VELOCITY} px/frame can already land on one.
 */
@Data
@Slf4j
@Prototype
@RequiredArgsConstructor
public final class StaticEnvironmentCollisionGrid implements GameEngineAware, GameMath {
    static final double SLOPE_LANDING_MIN_Y_VELOCITY = -1.75;
    static final int FLAT_SNAP_LIMIT_PIXELS = 6;
    static final int LEFT_FOOT_X = 4;
    static final int RIGHT_FOOT_X = 11;
    static final int SLOPE_CLIMB_LIMIT_PIXELS = 10;
    static final int SLOPE_STICK_PIXELS = 6;
    static final int WALKER_SLOPE_CLIMB_LIMIT_PIXELS = 8;
    static final Coordinates SLOPE_FEET_PROBE = of(8, 32);

    private final GameEngine gameEngine;
    private final PowerSwitchTimeWindow powerSwitchTimeWindow;

    private LevelObject[][] objects;
    private LevelObject[][] underlayObjects;
    private LevelSceneLayerType[][] surfaceLayers;
    private LevelSceneLayerType[][] underlayLayers;
    private LevelSceneDimensions dimensions;

    /**
     * The Y of the probe point that last hit a block, in level pixels: the head probe for a hit from below, and the
     * tail's or a kicked shell's probe for a hit from the side. A coin out of the block launches from this height.
     */
    private int bumpProbeY;

    /**
     * Collects whatever the players overlap while the P-Switch window is open, then counts the window down, in that
     * order so the window's last tick can still be collected.
     */
    public void tick() {
        if (powerSwitchTimeWindow.isActive()) {
            for (final Player player : gameEngine.getAllPlayers()) {
                if (player instanceof final LevelScenePlayer levelScenePlayer) {
                    resolvePlayerOverlaps(levelScenePlayer, levelScenePlayer.getObjectCollisionBounds());
                }
            }
        }
        countDownPowerSwitchTimeWindow();
    }

    /**
     * Counts the P-Switch window down by one tick and publishes {@link PowerSwitchExpired} on the tick it runs out.
     * Besides {@link #tick()}, the engine calls it on the ticks a player halts gameplay, as the window keeps counting
     * while the player goes through a pipe or grows.
     */
    public void countDownPowerSwitchTimeWindow() {
        if (powerSwitchTimeWindow.tick()) {
            gameEngine.publish(new PowerSwitchExpired());
        }
    }

    /**
     * Handles Player's collision against solid tiles (wall and ground).
     * Returns {@code true} if a horizontal wall hit occurred this frame.
     */
    public boolean handleCollision(
        final LevelScenePlayer levelScenePlayer,
        final boolean lowClearance
    ) {
        final PlayerPosition position = levelScenePlayer.getPosition();
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
        // The ceiling flag is cleared before anything else
        runtimeState.setHitCeiling(false);
        runtimeState.setSlopePull(0);
        final boolean playerIsMovingUp = position.getDY() < 0;
        final boolean playerIsLeftHalf = tileModulo(position.getX()) < 8;
        final boolean nearRamp = isNearRamp(levelScenePlayer);

        final CollisionProbe probe = resolveProbe(
            levelScenePlayer,
            playerIsMovingUp,
            playerIsLeftHalf,
            nearRamp
        );
        final ProbeLocation tVert = probe.vertical();
        final ProbeLocation tHoriz = probe.horizontal();

        final boolean solidVert = isSolidVert(levelScenePlayer, tVert, playerIsMovingUp, nearRamp);

        final boolean solidHoriz1 = isWallInFront(levelScenePlayer, tHoriz.first(), playerIsLeftHalf);
        final boolean solidHoriz2 = isWallInFront(levelScenePlayer, tHoriz.second(), playerIsLeftHalf);
        final boolean oneWayHoriz1 = isOneWayTileFromPlayer(levelScenePlayer, tHoriz.first());
        final boolean oneWayHoriz2 = isOneWayTileFromPlayer(levelScenePlayer, tHoriz.second());
        final boolean solidHoriz = (solidHoriz1 && !oneWayHoriz1) || (solidHoriz2 && !oneWayHoriz2);

        // Horizontal collision — eject the player from a wall, or slide them
        // off a corner.
        //
        // The player's X is nudged by ±1 pixel every frame whenever the
        // in-front probe detects a solid tile and the facing edge has not yet
        // reached a tile boundary — regardless of the player's horizontal
        // velocity. This ±1-per-frame slide (not a single full-overlap snap)
        // is what lets the player skid off the corner of a solid object they
        // jump into at its very edge: while rising with DX == 0 they drift
        // toward the free side, clear the corner, and fall back down instead
        // of catching on top of the object. Gating the position nudge behind a
        // "moving into the wall" (non-zero DX) test breaks this corner-slide,
        // because a straight-up jump has DX == 0.
        //
        // Velocity is a separate concern: the horizontal velocity is halted
        // only when the player is actually pushing into the wall, so a corner
        // slide preserves horizontal motion while a head-on wall hit stops it.
        //
        // Probe direction (playerIsLeftHalf):
        // - Left half (mod16 < 8): in-front probes check RIGHT (X+14), so
        //   the wall is to the right. Push left (dir = -1) and align the right
        //   edge (rightEdgeOffset).
        // - Right half (mod16 >= 8): in-front probes check LEFT (X+1), so
        //   the wall is to the left. Push right (dir = +1) and align the left
        //   edge (leftEdgeOffset).
        boolean hitWall = false;
        final int leftEdgeOffset = levelScenePlayer.isLarge() ? 2 : 3;
        final int rightEdgeOffset = levelScenePlayer.isLarge() ? 14 : 13;
        if (solidHoriz && !lowClearance) {
            // Touching a wall keeps the walk animation cycling: the walk
            // animation advances on any wall touch, before the alignment and
            // velocity checks.
            hitWall = true;

            final int dir = playerIsLeftHalf ? -1 : 1;
            final int edx = playerIsLeftHalf ? rightEdgeOffset : leftEdgeOffset;
            final double edgeX = position.getX() + edx;
            final double localX = tileModulo(edgeX);

            // Halt horizontal velocity whenever the player is pushing into the
            // wall (its sign opposes the ejection direction).
            // This runs EVERY frame the wall is detected, independent of the
            // positional-nudge gate below. If it were only applied on frames
            // that nudge (floor(localX) != 0), then on the "flush" frames
            // (facing edge already within [0,1) of the boundary, no nudge) the
            // input-driven DX would keep re-accumulating and push the player a
            // fraction of a pixel back into the wall each frame; once that
            // creeps past 1px the next frame nudges it back out — a 1px
            // drift/snap oscillation that shows up as the camera shaking left
            // and right while the player is held against a body. Corner-slide
            // is unaffected: a straight-up jump has DX == 0, which is not
            // "moving into the wall", so its velocity is preserved.
            final boolean movingIntoWall = (dir == 1 && position.getDX() < 0)
                || (dir == -1 && position.getDX() > 0);
            if (movingIntoWall) {
                position.setDX(0);
            }

            // Nudge only while the facing edge has not yet reached a tile
            // boundary. Once aligned the player has cleared the wall/corner,
            // so no further correction is applied. This alignment gate is also
            // what lets a flush, stationary player (e.g. the frame after an
            // emexit ends against the bounding tile) rest against the wall
            // without being repeatedly re-snapped — the previous
            // full-snap-plus-setDX(0) approach froze the camera in exactly
            // that situation.
            if (floor(localX) != 0) {
                position.addToX(dir);
            }
        }

        // Vertical collision
        final boolean slopeLandingAllowed = position.getDY() >= SLOPE_LANDING_MIN_Y_VELOCITY || !runtimeState.isInAir();
        if (nearRamp && slopeLandingAllowed && resolveSlopedGround(levelScenePlayer)) {
            return hitWall;
        }
        if (position.getDY() >= 0 || !runtimeState.isInAir()) {
            if (solidVert) {
                final double localY = tileModulo(floor(position.getY()));
                if (localY < 6) {
                    if (localY == 1) {
                        position.decrementY();
                    } else if (localY != 0) {
                        position.subtractFromY(2);
                    }
                    runtimeState.stop();
                    position.setDY(0);
                }
                handleStandingOnObjects(levelScenePlayer, tVert);
            } else if (!runtimeState.isInAir()) {
                // Walked off ledge
                position.setDY(0);
                runtimeState.fall();
            }
        } else {
            // Moving up
            if (solidVert) {
                // Head hitting objects. The ceiling flag is what lets the player into an upside-down pipe
                // (rising, airborne, head tile solid).
                runtimeState.setHitCeiling(true);
                position.setDY(GRAVITY_SLOW / TILE_SPRITE_SIZE);
                handleVerticalCollision(levelScenePlayer, tVert);
            }
        }
        return hitWall;
    }

    private void handleVerticalCollision(
        final LevelScenePlayer levelScenePlayer,
        final ProbeLocation tVert
    ) {
        final Coordinates cell = fromPlayerOffset(levelScenePlayer, tVert.first());
        if (isOutsideTheGrid(cell)) {
            return;
        }
        final LevelObject hitObject = getLevelObjectAt(cell);
        if (levelScenePlayer.getOrientation().getVertical() == UP) {
            bumpProbeY = (int) floor(levelScenePlayer.getPosition().getY()) + tVert.first().y();
            hitObject.onCollisionFromBelow(levelScenePlayer);
        }
    }

    /**
     * Dispatches {@link LevelObject#onCollisionFromAbove} to the tile(s) the player is resting on.
     *
     * <p>Unlike the upward case, the downward {@link ProbeLocation} holds two <em>distinct</em> points
     * — the left and right foot ({@code (4, 32)} and {@code (11, 32)} for both sizes, see
     * {@code CollisionOffsets}) — where the upward probe repeats a single point. Both are dispatched so a
     * tile is stomped whichever foot is over it, since the press test sits inside the tile detection
     * that runs for each probe. When both feet resolve to the same cell it is dispatched once.
     *
     * @param levelScenePlayer the player standing on the terrain
     * @param tVert            this frame's vertical probe pair (the feet, while descending/grounded)
     */
    private void handleStandingOnObjects(
        final LevelScenePlayer levelScenePlayer,
        final ProbeLocation tVert
    ) {
        final Coordinates leftFoot = fromPlayerOffset(levelScenePlayer, tVert.first());
        final Coordinates rightFoot = fromPlayerOffset(levelScenePlayer, tVert.second());
        dispatchStandingOn(levelScenePlayer, leftFoot);
        if (!rightFoot.equals(leftFoot)) {
            dispatchStandingOn(levelScenePlayer, rightFoot);
        }
    }

    private void dispatchStandingOn(
        final LevelScenePlayer levelScenePlayer,
        final Coordinates cell
    ) {
        if (isOutsideTheGrid(cell)) {
            return;
        }
        getLevelObjectAt(cell).onCollisionFromAbove(levelScenePlayer);
    }

    /**
     * Dispatches {@link LevelObject#onTailAttack} to the <b>single</b> tile containing the tail's probe
     * point: one tile lookup, whose result is bumped like a block hit from below.
     *
     * <p>This used to sweep every cell overlapped by the tail's <em>object</em> hitbox
     * ({@link LevelScenePlayer#getTailAttackBounds()}). That box is 15px tall, so it straddled two tile
     * rows whenever the player was not tile-aligned, and the tail would break a brick a whole row above
     * the real strike point. The object box remains correct for {@code ActiveObjectGrid}, where the
     * tail genuinely does a box overlap; only the terrain test is a point.
     *
     * @param levelScenePlayer the striking player
     * @param probeX           the probe point's X, in sprite-pixel space
     * @param probeY           the probe point's Y, in sprite-pixel space
     */
    public void resolveTailAttack(
        final LevelScenePlayer levelScenePlayer,
        final double probeX,
        final double probeY
    ) {
        final Coordinates probedCell = of(
            (int) floor(probeX / TILE_SPRITE_SIZE),
            (int) floor(probeY / TILE_SPRITE_SIZE)
        );
        final LevelObject object = getLevelObjectAt(probedCell);
        if (object != EMPTY_LEVEL_OBJECT) {
            bumpProbeY = (int) floor(probeY);
            object.onTailAttack(levelScenePlayer);
        }
    }


    /**
     * Dispatches {@link LevelObject#onPlayerOverlap} to every tile the player's body overlaps, solid or
     * not — the counterpart to the directional probes, and the path by which pass-through tiles act on
     * contact (a P-Switch-substituted brick collected as a coin).
     *
     * <p>A box sweep is used instead of four discrete tile probes (two at the feet, two at the head): for a
     * player one or two tiles tall those probes cover the same cells this box sweep does, and a box is
     * cheaper to keep correct than a second probe table.
     *
     * @param levelScenePlayer the player to test
     * @param bounds           the player's hitbox in sprite-pixel space
     */
    public void resolvePlayerOverlaps(
        final LevelScenePlayer levelScenePlayer,
        final AxisAlignedBoundingBox bounds
    ) {
        // Half-open edges: the right/bottom edge belongs to the next cell, so step back one pixel.
        final int firstColumn = (int) floor(bounds.left() / TILE_SPRITE_SIZE);
        final int lastColumn = (int) floor((bounds.right() - 1) / TILE_SPRITE_SIZE);
        final int firstRow = (int) floor(bounds.top() / TILE_SPRITE_SIZE);
        final int lastRow = (int) floor((bounds.bottom() - 1) / TILE_SPRITE_SIZE);

        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = firstColumn; column <= lastColumn; column++) {
                final LevelObject object = getLevelObjectAt(of(column, row));
                if (object != EMPTY_LEVEL_OBJECT) {
                    object.onPlayerOverlap(levelScenePlayer);
                }
            }
        }
    }

    public LevelObject getLevelObjectAt(final Coordinates offset) {
        try {
            return objects[offset.y()][offset.x()];
        }  catch (final ArrayIndexOutOfBoundsException e) {
            return EMPTY_LEVEL_OBJECT;
        }
    }

    /**
     * Retires the object at {@code offset}, exposing whatever tile sat directly beneath it.
     *
     * <p>The fallback slot is emptied as it is promoted, so retiring the same cell twice clears it rather
     * than handing back the same stand-in forever — which is what let a brick stacked over another brick
     * be broken indefinitely. The layer provenance moves in step with the object so a second break still
     * erases from the layer that actually paints the newly exposed tile.
     */
    public void removeLevelObjectAt(final Coordinates offset) {
        try {
            objects[offset.y()][offset.x()] = underlayObjects[offset.y()][offset.x()];
            underlayObjects[offset.y()][offset.x()] = EMPTY_LEVEL_OBJECT;
            if (surfaceLayers != null && underlayLayers != null) {
                surfaceLayers[offset.y()][offset.x()] = underlayLayers[offset.y()][offset.x()];
                underlayLayers[offset.y()][offset.x()] = null;
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            log.error("removeLevelObjectAt error", e);
        }
    }

    /**
     * Which layer paints the tile currently showing at {@code offset} — the layer whose baked texture has
     * to be erased when that tile is retired.
     *
     * @param offset the cell to look up
     * @return that cell's layer, falling back to {@code INTERACTIVE_OBJECTS} when unknown, since a
     *         tile-bound interactive object is what a caller retiring a cell is almost always holding
     */
    public LevelSceneLayerType getSourceLayerAt(final Coordinates offset) {
        if (surfaceLayers == null) {
            return INTERACTIVE_OBJECTS;
        }
        try {
            final LevelSceneLayerType sourceLayer = surfaceLayers[offset.y()][offset.x()];
            return sourceLayer == null ? INTERACTIVE_OBJECTS : sourceLayer;
        } catch (final ArrayIndexOutOfBoundsException e) {
            return INTERACTIVE_OBJECTS;
        }
    }

    public void placeLevelObjectAt(final Coordinates offset, final LevelObject levelObject) {
        try {
            objects[offset.y()][offset.x()] = levelObject;
        } catch (final ArrayIndexOutOfBoundsException e) {
            log.error("placeLevelObjectAt error", e);
        }
    }

    /**
     * Near a ramp, the feet leave the ramp itself to {@link #resolveSlopedGround(LevelScenePlayer)}: only flat ground
     * under a foot counts here.
     */
    private boolean isSolidVert(
        final Player player,
        final ProbeLocation tVert,
        final boolean playerIsMovingUp,
        final boolean nearRamp
    ) {
        final boolean feetIgnoreRamps = nearRamp && !playerIsMovingUp;
        final boolean solidVert1 = feetIgnoreRamps
            ? collidesIgnoringRampsAtOffset(player, tVert.first())
            : collidesAtOffset(player, tVert.first());
        final boolean solidVert2 = feetIgnoreRamps
            ? collidesIgnoringRampsAtOffset(player, tVert.second())
            : collidesAtOffset(player, tVert.second());
        final boolean solidVert;
        if (playerIsMovingUp) {
            final boolean oneWayVert1 = isOneWayTileFromPlayer(player, tVert.first());
            final boolean oneWayVert2 = isOneWayTileFromPlayer(player, tVert.second());
            solidVert = (solidVert1 && !oneWayVert1) || (solidVert2 && !oneWayVert2);
        } else {
            solidVert = solidVert1 || solidVert2;
        }
        return solidVert;
    }

    // -------------------------------------------------------------------------
    // Probe resolution
    // -------------------------------------------------------------------------

    private CollisionProbe resolveProbe(
        final LevelScenePlayer levelScenePlayer,
        final boolean movingUp,
        final boolean leftHalf,
        final boolean nearRamp
    ) {
        final boolean small = !levelScenePlayer.isLarge() || levelScenePlayer.getRuntimeState().isDucking();
        final DirectionalProbes probes = nearRamp
            ? (small ? SMALL_SLOPED_PROBES : LARGE_SLOPED_PROBES)
            : (small ? SMALL_PROBES : LARGE_PROBES);
        return probes.resolve(movingUp, leftHalf);
    }

    // -------------------------------------------------------------------------
    // Ramps
    // -------------------------------------------------------------------------

    /**
     * Whether a ramp lies under the player's feet, or close enough above or below them to be stepped onto this frame.
     */
    private boolean isNearRamp(final LevelScenePlayer levelScenePlayer) {
        final PlayerPosition position = levelScenePlayer.getPosition();
        final int x = (int) floor(position.getX());
        final int feetY = (int) floor(position.getY()) + SLOPE_FEET_PROBE.y();
        final int lastColumn = floorDiv(x + RIGHT_FOOT_X, TILE_SPRITE_SIZE);
        final int lastRow = floorDiv(feetY + SLOPE_STICK_PIXELS, TILE_SPRITE_SIZE);
        for (int column = floorDiv(x + LEFT_FOOT_X, TILE_SPRITE_SIZE); column <= lastColumn; column++) {
            for (int row = floorDiv(feetY - SLOPE_CLIMB_LIMIT_PIXELS, TILE_SPRITE_SIZE); row <= lastRow; row++) {
                if (isRampAt(of(column, row))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Stands the player on the ground under the centre of their feet, pixel by pixel, climbing onto a ramp's surface
     * or following it down. See {@code kb/slopes.md}, "Player on a slope".
     *
     * @return {@code false} when the ground under the centre is not something this can resolve (too deep to climb
     *         out of, or no ground at all while airborne), leaving it to the tile-based resolution
     */
    private boolean resolveSlopedGround(final LevelScenePlayer levelScenePlayer) {
        final PlayerPosition position = levelScenePlayer.getPosition();
        final int centreX = (int) floor(position.getX()) + SLOPE_FEET_PROBE.x();
        final int feetY = (int) floor(position.getY()) + SLOPE_FEET_PROBE.y();
        if (isSolidPixelForPlayer(levelScenePlayer, centreX, feetY)) {
            int surfaceY = feetY;
            while (isSolidPixelForPlayer(levelScenePlayer, centreX, surfaceY - 1)) {
                surfaceY--;
                if (feetY - surfaceY > SLOPE_CLIMB_LIMIT_PIXELS) {
                    return false;
                }
            }
            if (!isRampPixel(centreX, surfaceY) && feetY - surfaceY >= FLAT_SNAP_LIMIT_PIXELS) {
                return false;
            }
            standOnSlopedGround(levelScenePlayer, centreX, surfaceY);
            return true;
        }
        if (levelScenePlayer.getRuntimeState().isInAir()) {
            return false;
        }
        for (int surfaceY = feetY + 1; surfaceY <= feetY + SLOPE_STICK_PIXELS; surfaceY++) {
            if (isSolidPixelForPlayer(levelScenePlayer, centreX, surfaceY)) {
                standOnSlopedGround(levelScenePlayer, centreX, surfaceY);
                return true;
            }
        }
        return false;
    }

    private void standOnSlopedGround(
        final LevelScenePlayer levelScenePlayer,
        final int centreX,
        final int surfaceY
    ) {
        final PlayerPosition position = levelScenePlayer.getPosition();
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
        position.setY(surfaceY - SLOPE_FEET_PROBE.y());
        position.setDY(0);
        runtimeState.stop();
        final Coordinates groundCell = of(floorDiv(centreX, TILE_SPRITE_SIZE), floorDiv(surfaceY, TILE_SPRITE_SIZE));
        runtimeState.setSlopePull(findSlopeAt(groundCell).map(SlopeProfile::playerPull).orElse(0));
        dispatchStandingOn(levelScenePlayer, groundCell);
    }

    private boolean isRampPixel(
        final int x,
        final int y
    ) {
        return isRampAt(of(floorDiv(x, TILE_SPRITE_SIZE), floorDiv(y, TILE_SPRITE_SIZE)));
    }

    public boolean isRampAt(final Coordinates cell) {
        return findSlopeAt(cell).isPresent();
    }

    public Optional<SlopeProfile> findSlopeAt(final Coordinates cell) {
        return getLevelObjectAt(cell).findSlopeProfile();
    }

    /**
     * Finds the sloped ground a ground walker stands on: a ramp's surface, or the top of flat ground a ramp runs onto,
     * under pixel column {@code x} in the walker's feet row or the rows either side. It is found up to
     * {@value #WALKER_SLOPE_CLIMB_LIMIT_PIXELS} pixels above the feet (climbing) or {@code stickPixels} below them
     * (following the slope down).
     *
     * @return the surface's Y in level pixels, or empty when no sloped ground is that close
     */
    public OptionalInt findRampSurfaceNear(
        final int x,
        final double feetY,
        final int stickPixels
    ) {
        final int column = floorDiv(x, TILE_SPRITE_SIZE);
        final int feetRow = floorDiv((int) floor(feetY), TILE_SPRITE_SIZE);
        for (int row = feetRow - 1; row <= feetRow + 1; row++) {
            final OptionalInt surfaceY = slopedSurfaceAt(of(column, row), x);
            if (surfaceY.isEmpty()) {
                continue;
            }
            final int y = surfaceY.getAsInt();
            if (feetY >= y - stickPixels && feetY <= y + WALKER_SLOPE_CLIMB_LIMIT_PIXELS) {
                return surfaceY;
            }
        }
        return OptionalInt.empty();
    }

    /**
     * How far below its position to draw an object standing on a ramp, so that it glides along the straight ramp
     * behind the tile's pixels ({@link SlopeProfile#idealSurfaceDepthAt(double)}) instead of stepping a whole pixel at
     * a time. Drawing only, collision keeps the whole-pixel surface.
     *
     * @param centreX the object's centre, in level pixels with the fraction
     * @param feetY   the Y of its feet, in level pixels
     * @return the offset in pixels, or 0 when the feet are not on a ramp's surface
     */
    public double findSlopeDrawOffset(
        final double centreX,
        final double feetY
    ) {
        final int x = (int) floor(centreX);
        final Coordinates cell = of(floorDiv(x, TILE_SPRITE_SIZE), floorDiv((int) floor(feetY), TILE_SPRITE_SIZE));
        final double tileTop = cell.y() * (double) TILE_SPRITE_SIZE;
        return findSlopeAt(cell)
            .filter(slope -> tileTop + slope.surfaceDepthAt(x) == feetY)
            .map(slope -> tileTop + slope.idealSurfaceDepthAt(centreX - cell.x() * (double) TILE_SPRITE_SIZE) - feetY)
            .orElse(0.0);
    }

    /**
     * The player's {@link #findSlopeDrawOffset(double, double)}, measured under the centre of their feet, or 0 while
     * they are in the air.
     */
    public double findSlopeDrawOffset(final LevelScenePlayer levelScenePlayer) {
        if (levelScenePlayer.getRuntimeState().isInAir()) {
            return 0;
        }
        final PlayerPosition position = levelScenePlayer.getPosition();
        return findSlopeDrawOffset(
            position.getX() + SLOPE_FEET_PROBE.x(),
            floor(position.getY()) + SLOPE_FEET_PROBE.y()
        );
    }

    private OptionalInt slopedSurfaceAt(
        final Coordinates cell,
        final int x
    ) {
        final Optional<SlopeProfile> slope = findSlopeAt(cell);
        if (slope.isPresent()) {
            final int depth = slope.get().surfaceDepthAt(x);
            return depth < TILE_SPRITE_SIZE ? OptionalInt.of(cell.y() * TILE_SPRITE_SIZE + depth) : OptionalInt.empty();
        }
        final LevelObject levelObject = getLevelObjectAt(cell);
        final boolean flatGroundBesideARamp = levelObject.isCollidable() && !levelObject.isOneWayPlatform()
            && (isRampAt(of(cell.x() - 1, cell.y())) || isRampAt(of(cell.x() + 1, cell.y()))
            || isRampAt(of(cell.x() - 1, cell.y() - 1)) || isRampAt(of(cell.x() + 1, cell.y() - 1)));
        return flatGroundBesideARamp ? OptionalInt.of(cell.y() * TILE_SPRITE_SIZE) : OptionalInt.empty();
    }

    /**
     * Whether the pixel at {@code (x, y)} in level pixels is solid ground for the player: a ramp only at or below its
     * surface, and the level's sides as {@link #collidesAtOffset(Player, Coordinates)} has them.
     */
    private boolean isSolidPixelForPlayer(
        final Player player,
        final int x,
        final int y
    ) {
        final Coordinates cell = of(floorDiv(x, TILE_SPRITE_SIZE), floorDiv(y, TILE_SPRITE_SIZE));
        if (cell.x() < 0) {
            return true;
        }
        if (cell.x() >= dimensions.columns()) {
            return !(player instanceof final LevelScenePlayer levelScenePlayer && levelScenePlayer.isRunningOff());
        }
        if (cell.y() >= dimensions.rows()) {
            return false;
        }
        final LevelObject levelObject = getLevelObjectAt(cell);
        return levelObject
            .findSlopeProfile()
            .map(slope -> slope.isSolidAt(x, y))
            .orElseGet(levelObject::isCollidable);
    }

    /**
     * An in-front probe's test: a tile is a wall unless it can be walked up into from that side. See
     * {@link #isClimbableFromTheSide(Coordinates, boolean)}.
     *
     * @param probingRight whether the probe looks to the player's right
     */
    private boolean isWallInFront(
        final Player player,
        final Coordinates offset,
        final boolean probingRight
    ) {
        final Coordinates cell = fromPlayerOffset(player, offset);
        return collidesAtOffset(player, offset) && !isClimbableFromTheSide(cell, probingRight);
    }

    /**
     * Whether a walker meeting this solid tile from the side walks up into it instead of against it: a ramp, or ground
     * that a ramp on that side leads up onto. The second is met from the side when a walker climbs fast enough to sink
     * into the slope for a frame. Anything else is a wall, even with a ramp on top of it: a step is reached from the
     * flat, not up a slope.
     *
     * @param approachedFromTheLeft whether the walker comes from the tile's left
     */
    public boolean isClimbableFromTheSide(
        final Coordinates cell,
        final boolean approachedFromTheLeft
    ) {
        if (isRampAt(cell)) {
            return true;
        }
        final int sideColumn = approachedFromTheLeft ? cell.x() - 1 : cell.x() + 1;
        final int edgeFacingTheTile = approachedFromTheLeft ? TILE_SPRITE_SIZE - 1 : 0;
        return findSlopeAt(of(sideColumn, cell.y()))
            .map(slope -> slope.surfaceDepthAt(edgeFacingTheTile) < TILE_SPRITE_SIZE / 2)
            .orElse(false);
    }

    /**
     * Like {@link #collidesAtOffset(Player, Coordinates)}, except that a ramp is not solid at all.
     */
    private boolean collidesIgnoringRampsAtOffset(
        final Player player,
        final Coordinates offset
    ) {
        return !isRampAt(fromPlayerOffset(player, offset)) && collidesAtOffset(player, offset);
    }

    /**
     * Checks whether the object at the given {@link Coordinates} from the player
     * position is solid (i.e. {@link LevelObject#isCollidable()} returns {@code true}). A ramp is solid only at or
     * below its surface.
     *
     * <p>Unlike the object-facing {@link #isWallOrCeilingSolidTile(Coordinates)}, the level's left and
     * right edges are solid here: the player is held inside the level, the same job a screen-edge clamp
     * does while scrolling. In the end-level run-off the right edge is open, so the player walks out of
     * the level instead of stopping against it.
     *
     * <p>Below the level is open. The player's probe finds no tile at all once it reaches the level's
     * bottom, so a pit has no floor and the player falls through it.
     */
    public boolean collidesAtOffset(
        final Player player,
        final Coordinates offset
    ) {
        final PlayerPosition position = player.getPosition();
        return isSolidPixelForPlayer(
            player,
            (int) floor(position.getX()) + offset.x(),
            (int) floor(position.getY()) + offset.y()
        );
    }

    /**
     * Checks whether the object at the given {@link Coordinates} from the player
     * position is a one-way platform.
     */
    public boolean isOneWayTileFromPlayer(
        final Player player,
        final Coordinates offset
    ) {
        final Coordinates cell = fromPlayerOffset(player, offset);

        if (isOutsideTheGrid(cell)) {
            return false;
        }

        return getLevelObjectAt(cell).isOneWayPlatform();
    }

    /**
     * Queries the scene's static terrain for a probe that is <b>falling onto</b> a tile: is the tile at
     * {@code (column, row)} something an object can come to rest on? This is the first solidity tier,
     * solid at the ground (a player can stand on it), so it includes the jump-through platforms, which
     * is the whole point of them.
     *
     * <p>Off the left or right edge is open: the level's sides are not a wall to an object, which walks
     * on and is deleted once it is past the screen's delete edge. An object's tile probe reads an empty,
     * non-solid tile for any X outside the level. Above and below the level are open too: the same holds
     * for a Y at or past the level's bottom, so an object walks off into a pit and falls out of the
     * level. The player's probes are {@link #collidesAtOffset(Player, Coordinates)}, which keeps both
     * sides solid.
     *
     * @see #isWallOrCeilingSolidTile(Coordinates) for the head-and-walls tier
     */
    public boolean isGroundSolidTile(final Coordinates cell) {
        if (isOutsideTheLevel(cell)) {
            return false;
        }
        return getLevelObjectAt(cell).isCollidable();
    }

    /**
     * Queries the scene's static terrain for a probe that is <b>rising into</b> a tile or <b>walking
     * into</b> it: is the tile at {@code (column, row)} solid to a head or a side? This is the second
     * solidity tier, solid at the head and walls, which the jump-through platforms are deliberately
     * below.
     *
     * <p>The two tiers are not a player concession. The shared object tile detection uses exactly this
     * split: the wall test and the ceiling test both check the head-and-walls tier, while the floor
     * test, taken when the object's vertical velocity is zero or downward, checks the ground tier. So a
     * panel top stops a falling Goomba and is thin air to a hopping Para-Goomba's head and to anything
     * walking into its side — the same thing the player sees.
     *
     * <p>Bounds behave as in {@link #isGroundSolidTile(Coordinates)}: everything outside the level is open,
     * so an object walking into one of its sides keeps going instead of turning around at it.
     */
    public boolean isWallOrCeilingSolidTile(final Coordinates cell) {
        if (isOutsideTheLevel(cell)) {
            return false;
        }
        final LevelObject levelObject = getLevelObjectAt(cell);
        return levelObject.isCollidable() && !levelObject.isOneWayPlatform();
    }

    /** Whether the cell lies beside, above or below the level. */
    private boolean isOutsideTheLevel(final Coordinates cell) {
        return isOutside(cell, gameEngine.getLevelScene().getDimensions());
    }

    /** Whether the cell lies beside, above or below this grid. */
    private boolean isOutsideTheGrid(final Coordinates cell) {
        return isOutside(cell, dimensions);
    }

    private static boolean isOutside(final Coordinates cell, final LevelSceneDimensions bounds) {
        return cell.x() < 0 || cell.x() >= bounds.columns() || cell.y() < 0 || cell.y() >= bounds.rows();
    }

    /**
     * Finds the live {@link LevelScenePlayer} whose position is nearest to the given point, by
     * squared Euclidean distance in sprite-pixel space. Active objects that must react to "the
     * player" — e.g. a Super Mushroom choosing which way to roll away — use this instead of
     * assuming a single player, so they behave correctly in multiplayer.
     *
     * @param x the reference point's X in sprite-pixel space
     * @param y the reference point's Y in sprite-pixel space
     * @return the closest level-scene player, or empty if there are none
     */
    public Optional<LevelScenePlayer> findClosestPlayerTo(
        final double x,
        final double y
    ) {
        LevelScenePlayer closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (final Player player : gameEngine.getAllPlayers()) {
            if (player instanceof final LevelScenePlayer levelScenePlayer) {
                final PlayerPosition position = levelScenePlayer.getPosition();
                final double dx = position.getX() - x;
                final double dy = position.getY() - y;
                final double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared < closestDistanceSquared) {
                    closestDistanceSquared = distanceSquared;
                    closest = levelScenePlayer;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    public boolean isBlockBumpActiveAt(final Coordinates cellOffset) {
        for (final BlockMotionManager blockMotionManager : getMotionManagers(BlockMotionManager.class)) {
            if (blockMotionManager.isBlockBumpActiveAt(cellOffset)) {
                return true;
            }
        }
        return false;
    }
}
