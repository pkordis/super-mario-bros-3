package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import lombok.Data;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

@Data
public class PlayerPosition {
    /** Position in sprite-pixel space (1 tile = 16 sprite-pixels). */
    private double x;
    private double y;

    /** Velocity in sprite-pixels per frame. */
    private double dX;
    private double dY;

    /** Previous-tick position for render interpolation. */
    private double prevX;
    private double prevY;

    /**
     * Snapshots the current position into prevX/prevY. Must be called
     * once at the start of each simulation tick, before physics runs.
     */
    public void snapshotPrevious() {
        prevX = x;
        prevY = y;
    }

    public void incrementX() {
        setX(x + 1);
    }

    public void incrementY() {
        setY(y + 1);
    }

    public void addToX(final double toAdd) {
        setX(x + toAdd);
    }

    public void addToY(final double toAdd) {
        setY(y + toAdd);
    }

    public void addToDX(final double toAdd) {
        setDX(dX + toAdd);
    }

    public void addToDY(final double toAdd) {
        setDY(dY + toAdd);
    }

    public void subtractFromDX(final double toSubtract) {
        setDX(dX - toSubtract);
    }

    public void subtractFromDY(final double toSubtract) {
        setDY(dY - toSubtract);
    }

    public void decrementY() {
        setY(y - 1);
    }

    public void subtractFromY(final double toSubtract) {
        setY(y - toSubtract);
    }

    public PlayerPosition interpolateBetweenPreviousAndCurrent(final double alpha) {
        final double interpolatedX = getPrevX() + (getX() - getPrevX()) * alpha;
        final double interpolatedY = getPrevY() + (getY() - getPrevY()) * alpha;
        final PlayerPosition interpolatedPosition = new PlayerPosition();
        interpolatedPosition.setX(interpolatedX);
        interpolatedPosition.setY(interpolatedY);
        return interpolatedPosition;
    }

    public PlayerPosition toTileUnitBased(final LevelSceneDimensions dimensions) {
        final PlayerPosition tilePosition = new PlayerPosition();
        final double gameX = (getX() / TILE_SPRITE_SIZE);
        final double gameY = (dimensions.rows() - (getY() / TILE_SPRITE_SIZE));
        tilePosition.setX(gameX);
        tilePosition.setY(gameY);
        return tilePosition;
    }
}
