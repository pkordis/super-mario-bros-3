package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.TileOffset;
import house.x1337.app.smb3.model.game.collision.CollisionProbe;
import house.x1337.app.smb3.model.game.collision.DirectionalProbes;
import house.x1337.app.smb3.model.game.collision.ProbeLocation;
import house.x1337.app.smb3.model.game.player.ActivePlayerState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.GameMath;

import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.enumeration.TileType.Category.COLLIDING;
import static house.x1337.app.smb3.enumeration.TileType.PANEL_WALKABLE_TOP;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static java.lang.Math.floor;

public record CollisionGrid(
    Player player,
    Tile[][] tiles,
    int gridRows,
    int gridColumns
) implements GameMath {
    public void handleCollision(
        final int initialHeightOffset,
        final boolean lowClearance
    ) {
        final PlayerPosition position = player.getPosition();
        final ActivePlayerState playerState = player.getState();
        final boolean playerIsMovingUp = position.getDY() < 0;
        final boolean playerIsLeftHalf = mod16(position.getX()) < 8;

        final CollisionProbe probe = resolveProbe(playerIsMovingUp, playerIsLeftHalf);
        final ProbeLocation tVert = probe.vertical();
        final ProbeLocation tHoriz = probe.horizontal();

        final boolean solidVert = isSolidVert(tVert, playerIsMovingUp);

        final boolean solidHoriz1 = collidesAtOffset(tHoriz.first());
        final boolean solidHoriz2 = collidesAtOffset(tHoriz.second());
        final boolean oneWayHoriz1 = isOneWayTileFromPlayer(tHoriz.first());
        final boolean oneWayHoriz2 = isOneWayTileFromPlayer(tHoriz.second());
        final boolean solidHoriz = (solidHoriz1 && !oneWayHoriz1) || (solidHoriz2 && !oneWayHoriz2);

        // Horizontal collision
        final int leftEdgeOffset = player.isLarge() ? 2 : 3;
        final int rightEdgeOffset = player.isLarge() ? 14 : 13;
        if (solidHoriz && !lowClearance) {
            final int dir = playerIsLeftHalf ? -1 : 1;
            final int edx = playerIsLeftHalf ? leftEdgeOffset : rightEdgeOffset;
            final double edgeX = position.getX() + edx;
            final double localX = mod16(edgeX);
            if (floor(localX) != 0) {
                position.addToX(dir);
                if ((position.getDX() < 0 && dir == 1) || (position.getDX() >= 0 && dir == -1)) {
                    position.setDX(0);
                }
            }
        }

        final ActivePlayerState state = player.getState();

        // Vertical collision
        if (position.getDY() >= 0 || !state.isInAir()) {
            if (solidVert) {
                final double localY = mod16(floor(position.getY()));
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
                // Hit head
                position.setDY(GRAVITY_SLOW / 16.0);
            }
        }
    }

    private boolean isSolidVert(ProbeLocation tVert, boolean playerIsMovingUp) {
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
        final DirectionalProbes probes = (!player.isLarge() || player.getState().isDucking())
            ? SMALL_PROBES
            : LARGE_PROBES;
        return probes.resolve(movingUp, leftHalf);
    }

    // -------------------------------------------------------------------------
    // Tile query helpers
    // -------------------------------------------------------------------------

    /**
     * Checks whether the tile at the given pixel offset from the player
     * position is a solid (COLLIDING category) tile.
     */
    public boolean collidesAtOffset(final int dx, final int dy) {
        return collidesAtOffset(new Offset(dx, dy));
    }

    /**
     * Checks whether the tile at the given {@link Offset} from the player
     * position is a solid (COLLIDING category) tile.
     */
    public boolean collidesAtOffset(final Offset offset) {
        final TileOffset tileOffset = TileOffset.fromPlayerOffset(player, offset);
        final int tx = tileOffset.getX();
        int ty = tileOffset.getY();

        if (ty < 0) {
            ty = 0;
        } else if (ty >= gridRows) {
            return true; // Below world = solid
        }
        if (tx < 0 || tx >= gridColumns) {
            return true; // Out of horizontal bounds = solid
        }

        final Tile tile = tiles[ty][tx];
        return tile != null
            && tile.getType() != null
            && tile.getType().getCategory() == COLLIDING;
    }

    /**
     * Checks whether the tile at the given pixel offset from the player
     * position is a one-way platform.
     */
    public boolean isOneWayTileFromPlayer(final int dx, final int dy) {
        return isOneWayTileFromPlayer(new Offset(dx, dy));
    }

    /**
     * Checks whether the tile at the given {@link Offset} from the player
     * position is a one-way platform.
     */
    public boolean isOneWayTileFromPlayer(final Offset offset) {
        final TileOffset tileOffset = TileOffset.fromPlayerOffset(player, offset);
        final int tx = tileOffset.getX();
        final int ty = tileOffset.getY();

        if (ty < 0 || ty >= gridRows || tx < 0 || tx >= gridColumns) {
            return false;
        }

        final Tile tile = tiles[ty][tx];
        return tile != null && tile.getType() == PANEL_WALKABLE_TOP;
    }
}
