package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.brick.BrickBlockWithoutReward;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelObjectOffset;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.CollisionProbe;
import house.x1337.app.smb3.model.game.collision.DirectionalProbes;
import house.x1337.app.smb3.model.game.collision.ProbeLocation;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.util.GameMath;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.model.game.LevelObjectOffset.fromPlayerOffset;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static java.lang.Math.floor;

@Getter
@Slf4j
@RequiredArgsConstructor
public final class CollisionGrid implements GameMath {
    private final LevelScenePlayer levelScenePlayer;
    private final LevelObject[][] objects;
    private final LevelSceneDimensions dimensions;
    private final GameEngine gameEngine;

    /**
     * Handles Player's collision against solid tiles (wall and ground).
     * Returns {@code true} if a horizontal wall hit occurred this frame
     * (dasm prg008 Player_DetectSolids: wall hit detection at PRG008_B4F3).
     */
    public boolean handleCollision(
        final int initialHeightOffset,
        final boolean lowClearance
    ) {
        final PlayerPosition position = levelScenePlayer.getPosition();
        final ActivePlayerState playerState = levelScenePlayer.getState();
        final boolean playerIsMovingUp = position.getDY() < 0;
        final boolean playerIsLeftHalf = tileModulo(position.getX()) < 8;

        final CollisionProbe probe = resolveProbe(playerIsMovingUp, playerIsLeftHalf);
        final ProbeLocation tVert = probe.vertical();
        final ProbeLocation tHoriz = probe.horizontal();

        final boolean solidVert = isSolidVert(tVert, playerIsMovingUp);

        final boolean solidHoriz1 = collidesAtOffset(tHoriz.first());
        final boolean solidHoriz2 = collidesAtOffset(tHoriz.second());
        final boolean oneWayHoriz1 = isOneWayTileFromPlayer(tHoriz.first());
        final boolean oneWayHoriz2 = isOneWayTileFromPlayer(tHoriz.second());
        final boolean solidHoriz = (solidHoriz1 && !oneWayHoriz1) || (solidHoriz2 && !oneWayHoriz2);

        // Horizontal collision — eject player from wall (dasm prg008
        // PRG008_B4F3). When probes detect a solid tile, snap the player
        // so their edge aligns exactly to the wall's tile boundary.
        //
        // The probe direction is determined by playerIsLeftHalf:
        // - Left half (mod16 < 8): probes check RIGHT (X+0x0E). Wall is
        //   to the right. Snap the right edge (rightEdgeOffset) to the
        //   wall boundary by subtracting the overlap.
        // - Right half (mod16 >= 8): probes check LEFT (X+0x01). Wall is
        //   to the left. Snap the left edge (leftEdgeOffset) to the wall
        //   boundary by adding the gap to the next boundary.
        boolean hitWall = false;
        final int leftEdgeOffset = levelScenePlayer.isLarge() ? 2 : 3;
        final int rightEdgeOffset = levelScenePlayer.isLarge() ? 14 : 13;
        if (solidHoriz && !lowClearance) {
            // Only apply wall correction when the player is moving INTO
            // the wall. If moving away from the wall, the player is
            // naturally leaving the collision zone — skip correction to
            // avoid catapulting them in the wrong direction.
            // DX == 0 (stationary) is deliberately excluded: after emexit
            // ends, the player exits with DX = 0 while positioned against
            // the wall that bounds the emexit end object. Treating DX = 0
            // as "into-wall" permanently freezes the player because the
            // snap zeroes DX again, recreating the same condition next frame.
            // A stationary player is not pushing into anything; positional
            // overlap from a prior frame is corrected naturally on the next
            // frame that has a non-zero DX.
            final boolean movingIntoWall = playerIsLeftHalf
                    ? position.getDX() > 0
                    : position.getDX() < 0;

            if (movingIntoWall) {
                hitWall = true;

                // Check the edge that faces the wall for misalignment.
                final int edx = playerIsLeftHalf ? rightEdgeOffset : leftEdgeOffset;
                final double edgeX = position.getX() + edx;
                final double localX = tileModulo(edgeX);

                if (localX > 0.001 && localX < TILE_SPRITE_SIZE - 0.001) {
                    if (playerIsLeftHalf) {
                        // Wall on right: the right edge has penetrated into
                        // the wall tile. Push left by localX to align the
                        // edge to the wall boundary.
                        position.addToX(-localX);
                    } else {
                        // Wall on left: determine whether the edge is still
                        // inside the wall tile or has already cleared it.
                        // Compare the edge's tile column with the probe's.
                        final int probeX = (int) floor(position.getX())
                                + tHoriz.first().x();
                        final int probeTileCol = Math.floorDiv(probeX, TILE_SPRITE_SIZE);
                        final int edgeTileCol = Math.floorDiv(
                                (int) floor(edgeX), TILE_SPRITE_SIZE);
                        if (edgeTileCol <= probeTileCol) {
                            // Edge is still inside (or at) the wall tile.
                            // Push right to the next boundary.
                            position.addToX(TILE_SPRITE_SIZE - localX);
                        } else {
                            // Edge already cleared the wall — push left to
                            // snap back to the boundary it just crossed.
                            position.addToX(-localX);
                        }
                    }
                }
                position.setDX(0);
            }
        }

        final ActivePlayerState state = levelScenePlayer.getState();

        // Vertical collision
        if (position.getDY() >= 0 || !state.isInAir()) {
            if (solidVert) {
                final double localY = tileModulo(floor(position.getY()));
                if (localY < 6) {
                    if (localY == 1) {
                        position.decrementY();
                    } else if (localY != 0) {
                        position.subtractFromY(2);
                    }
                    playerState.stop();
                    position.setDY(0);
                }
            } else if (!state.isInAir()) {
                // Walked off ledge
                position.setDY(0);
                playerState.fall();
            }
        } else {
            // Moving up
            if (solidVert) {
                // Head hitting objects
                position.setDY(GRAVITY_SLOW / TILE_SPRITE_SIZE);
                handleVerticalCollision(tVert);
            }
        }
        return hitWall;
    }

    /**
     * Checks whether the tile struck during a head-hit is a {@link BrickBlockWithoutReward}
     * and, if so, removes it from the collision grid and triggers the appropriate animation.
     *
     * <p>Uses {@code tVert.first()} — the single upward probe shared by both left-half and
     * right-half large-player probes (Offset(0x08, 0x06)) — to resolve the hit tile's
     * grid coordinates.
     *
     * @param tVert vertical probe for the current move direction
     */
    private void handleVerticalCollision(final ProbeLocation tVert) {
        final LevelObjectOffset objectOffset = fromPlayerOffset(levelScenePlayer, tVert.first());
        if (objectOffset.isOutsideOf(this)) {
            return;
        }
        final LevelObject hitObject = getLevelObjectAt(objectOffset);
        if (levelScenePlayer.getPlayerOrientationVertical() == UP) {
            hitObject.onCollisionFromBelow(levelScenePlayer);
        }
    }

    public LevelObject getLevelObjectAt(final Offset offset) {
        try {
            return objects[offset.y()][offset.x()];
        }  catch (final ArrayIndexOutOfBoundsException e) {
            return EMPTY_LEVEL_OBJECT;
        }
    }

    public void removeLevelObjectAt(final Offset offset) {
        try {
            objects[offset.y()][offset.x()] = EMPTY_LEVEL_OBJECT;
        } catch (final ArrayIndexOutOfBoundsException e) {
            log.error("removeLevelObjectAt error", e);
        }
    }

    private boolean isSolidVert(
        final ProbeLocation tVert,
        final boolean playerIsMovingUp
    ) {
        final boolean solidVert1 = collidesAtOffset(tVert.first());
        final boolean solidVert2 = collidesAtOffset(tVert.second());
        final boolean solidVert;
        if (playerIsMovingUp) {
            final boolean oneWayVert1 = isOneWayTileFromPlayer(tVert.first());
            final boolean oneWayVert2 = isOneWayTileFromPlayer(tVert.second());
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
        final boolean movingUp,
        final boolean leftHalf
    ) {
        final DirectionalProbes probes = (!levelScenePlayer.isLarge() || levelScenePlayer.getState().isDucking())
            ? SMALL_PROBES
            : LARGE_PROBES;
        return probes.resolve(movingUp, leftHalf);
    }

    // -------------------------------------------------------------------------
    // Object query helpers
    // -------------------------------------------------------------------------

    /**
     * Checks whether the object at the given pixel offset from the player
     * position is solid (i.e. {@link LevelObject#isCollidable()} returns {@code true}).
     */
    public boolean collidesAtOffset(final int dx, final int dy) {
        return collidesAtOffset(Offset.of(dx, dy));
    }

    /**
     * Checks whether the object at the given {@link Offset} from the player
     * position is solid (i.e. {@link LevelObject#isCollidable()} returns {@code true}).
     */
    public boolean collidesAtOffset(final Offset offset) {
        final LevelObjectOffset levelObjectOffset = fromPlayerOffset(levelScenePlayer, offset);
        final int tx = levelObjectOffset.x();
        int ty = levelObjectOffset.y();

        if (ty < 0) {
            ty = 0;
        } else if (ty >= dimensions.rows()) {
            return true; // Below world = solid
        }
        if (tx < 0 || tx >= dimensions.columns()) {
            return true; // Out of horizontal bounds = solid
        }

        return getLevelObjectAt(levelObjectOffset).isCollidable();
    }

    /**
     * Checks whether the object at the given pixel offset from the player
     * position is a one-way platform.
     */
    public boolean isOneWayTileFromPlayer(final int dx, final int dy) {
        return isOneWayTileFromPlayer(Offset.of(dx, dy));
    }

    /**
     * Checks whether the object at the given {@link Offset} from the player
     * position is a one-way platform.
     */
    public boolean isOneWayTileFromPlayer(final Offset offset) {
        final LevelObjectOffset levelObjectOffset = fromPlayerOffset(levelScenePlayer, offset);

        if (levelObjectOffset.isOutsideOf(this)) {
            return false;
        }

        return getLevelObjectAt(levelObjectOffset).isOneWayPlatform();
    }
}
