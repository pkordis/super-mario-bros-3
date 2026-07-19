package house.x1337.app.smb3.model.game.player;

import lombok.Data;

@Data
public class PlayerPosition {
    /** Position in sprite-pixel space (1 tile = 16 sprite-pixels). */
    private double x;
    private double y;

    /** Velocity in sprite-pixels per frame. */
    private double dX;
    private double dY;

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
}
