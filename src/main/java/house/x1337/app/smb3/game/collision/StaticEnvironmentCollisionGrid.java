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
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.util.GameMath;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.model.game.Coordinates.fromPlayerOffset;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static java.lang.Math.floor;

@Data
@Slf4j
@Prototype
@RequiredArgsConstructor
public final class StaticEnvironmentCollisionGrid implements GameEngineAware, GameMath {
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
        final boolean playerIsMovingUp = position.getDY() < 0;
        final boolean playerIsLeftHalf = tileModulo(position.getX()) < 8;

        final CollisionProbe probe = resolveProbe(
            levelScenePlayer,
            playerIsMovingUp,
            playerIsLeftHalf
        );
        final ProbeLocation tVert = probe.vertical();
        final ProbeLocation tHoriz = probe.horizontal();

        final boolean solidVert = isSolidVert(levelScenePlayer, tVert, playerIsMovingUp);

        final boolean solidHoriz1 = collidesAtOffset(levelScenePlayer, tHoriz.first());
        final boolean solidHoriz2 = collidesAtOffset(levelScenePlayer, tHoriz.second());
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

    private boolean isSolidVert(
        final Player player,
        final ProbeLocation tVert,
        final boolean playerIsMovingUp
    ) {
        final boolean solidVert1 = collidesAtOffset(player, tVert.first());
        final boolean solidVert2 = collidesAtOffset(player, tVert.second());
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
        final boolean leftHalf
    ) {
        final DirectionalProbes probes = (!levelScenePlayer.isLarge() || levelScenePlayer.getRuntimeState().isDucking())
            ? SMALL_PROBES
            : LARGE_PROBES;
        return probes.resolve(movingUp, leftHalf);
    }

    /**
     * Checks whether the object at the given {@link Coordinates} from the player
     * position is solid (i.e. {@link LevelObject#isCollidable()} returns {@code true}).
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
        final Coordinates cell = fromPlayerOffset(player, offset);

        if (cell.x() < 0) {
            return true; // Left of the level = solid
        }
        if (cell.x() >= dimensions.columns()) {
            // Right of the level = solid, but open to a level player running off it
            return !(player instanceof final LevelScenePlayer levelScenePlayer && levelScenePlayer.isRunningOff());
        }
        if (cell.y() >= dimensions.rows()) {
            return false; // Below the level = open
        }

        return getLevelObjectAt(cell).isCollidable();
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
