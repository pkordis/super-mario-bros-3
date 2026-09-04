package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelObjectOffset;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.CollisionProbe;
import house.x1337.app.smb3.model.game.collision.DirectionalProbes;
import house.x1337.app.smb3.model.game.collision.ProbeLocation;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.util.GameMath;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.model.game.LevelObjectOffset.fromPlayerOffset;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static java.lang.Math.floor;

@Slf4j
@Getter
@RequiredArgsConstructor
public final class StaticEnvironmentCollisionGrid implements GameMath {
    private final LevelObject[][] objects;
    private final LevelSceneDimensions dimensions;
    private final GameEngine gameEngine;

    /**
     * Handles Player's collision against solid tiles (wall and ground).
     * Returns {@code true} if a horizontal wall hit occurred this frame
     * (dasm prg008 Player_DetectSolids: wall hit detection at PRG008_B4F3).
     */
    public boolean handleCollision(
        final LevelScenePlayer levelScenePlayer,
        final boolean lowClearance
    ) {
        final PlayerPosition position = levelScenePlayer.getPosition();
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
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
        // off a corner (dasm prg008 Player_DetectSolids @ PRG008_B4F3; JS
        // reference index.html "eject player from wall").
        //
        // The original nudges Player_X by ±1 pixel every frame whenever the
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
        // Velocity is a separate concern (dasm PRG008_B52F): XVel is halted
        // only when the player is actually pushing into the wall, so a corner
        // slide preserves horizontal motion while a head-on wall hit stops it.
        //
        // Probe direction (playerIsLeftHalf):
        // - Left half (mod16 < 8): in-front probes check RIGHT (X+0x0E), so
        //   the wall is to the right. Push left (dir = -1) and align the right
        //   edge (rightEdgeOffset).
        // - Right half (mod16 >= 8): in-front probes check LEFT (X+0x01), so
        //   the wall is to the left. Push right (dir = +1) and align the left
        //   edge (leftEdgeOffset).
        boolean hitWall = false;
        final int leftEdgeOffset = levelScenePlayer.isLarge() ? 2 : 3;
        final int rightEdgeOffset = levelScenePlayer.isLarge() ? 14 : 13;
        if (solidHoriz && !lowClearance) {
            // Touching a wall keeps the walk animation cycling (dasm
            // PRG008_B4F3: INC Player_WalkAnimTicks fires on any wall touch,
            // before the alignment and velocity checks).
            hitWall = true;

            final int dir = playerIsLeftHalf ? -1 : 1;
            final int edx = playerIsLeftHalf ? rightEdgeOffset : leftEdgeOffset;
            final double edgeX = position.getX() + edx;
            final double localX = tileModulo(edgeX);

            // Halt horizontal velocity whenever the player is pushing into the
            // wall (its sign opposes the ejection direction) — dasm PRG008_B52F.
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
            } else if (!runtimeState.isInAir()) {
                // Walked off ledge
                position.setDY(0);
                runtimeState.fall();
            }
        } else {
            // Moving up
            if (solidVert) {
                // Head hitting objects
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
        final LevelObjectOffset objectOffset = fromPlayerOffset(levelScenePlayer, tVert.first());
        if (objectOffset.isOutsideOf(this)) {
            return;
        }
        final LevelObject hitObject = getLevelObjectAt(objectOffset);
        if (levelScenePlayer.getOrientation().getVertical() == UP) {
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

    public void placeLevelObjectAt(final Offset offset, final LevelObject levelObject) {
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
     * Checks whether the object at the given {@link Offset} from the player
     * position is solid (i.e. {@link LevelObject#isCollidable()} returns {@code true}).
     */
    public boolean collidesAtOffset(
        final Player player,
        final Offset offset
    ) {
        final LevelObjectOffset levelObjectOffset = fromPlayerOffset(player, offset);
        final int tx = levelObjectOffset.x();
        int ty = levelObjectOffset.y();

        if (ty >= dimensions.rows()) {
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
    public boolean isOneWayTileFromPlayer(
        final Player player,
        final int dx,
        final int dy
    ) {
        return isOneWayTileFromPlayer(player, Offset.of(dx, dy));
    }

    /**
     * Checks whether the object at the given {@link Offset} from the player
     * position is a one-way platform.
     */
    public boolean isOneWayTileFromPlayer(
        final Player player,
        final Offset offset
    ) {
        final LevelObjectOffset levelObjectOffset = fromPlayerOffset(player, offset);

        if (levelObjectOffset.isOutsideOf(this)) {
            return false;
        }

        return getLevelObjectAt(levelObjectOffset).isOneWayPlatform();
    }

    /**
     * Queries the scene's static terrain: is the tile at {@code (column, row)} solid? Off the left
     * or right edge, and below the world, count as solid so the mushroom cannot escape sideways or
     * fall through the floor; above the world (negative row) is open sky.
     */
    public boolean isSolidTile(final int column, final int row) {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final int columns = gameEngine.getLevelScene().getDimensions().columns();
        if (row < 0) {
            return false;
        }
        if (row >= rows) {
            return true;
        }
        if (column < 0 || column >= columns) {
            return true;
        }
        return getLevelObjectAt(Offset.of(column, row)).isCollidable();
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
}
