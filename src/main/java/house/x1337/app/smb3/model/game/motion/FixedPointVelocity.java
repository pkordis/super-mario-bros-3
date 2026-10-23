package house.x1337.app.smb3.model.game.motion;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.min;

public record FixedPointVelocity(int x, int y) {
    public static final FixedPointVelocity STILL = new FixedPointVelocity(0, 0);
    private static final double TO_PIXELS = 1.0 / TILE_SPRITE_SIZE;

    public double pixelStepX() {
        return x * TO_PIXELS;
    }

    public double pixelStepY() {
        return y * TO_PIXELS;
    }

    public FixedPointVelocity withX(final int x) {
        return new FixedPointVelocity(x, this.y);
    }

    public FixedPointVelocity withY(final int y) {
        return new FixedPointVelocity(this.x, y);
    }

    public FixedPointVelocity plusX(final int dx) {
        return new FixedPointVelocity(x + dx, y);
    }

    public FixedPointVelocity plusY(final int dy) {
        return new FixedPointVelocity(x, y + dy);
    }

    public FixedPointVelocity negatedX() {
        return withX(-x);
    }

    public FixedPointVelocity directedX(
        final boolean facingRight,
        final int speed
    ) {
        return withX(facingRight ? speed : -speed);
    }

    public FixedPointVelocity falling(
        final int fallRate,
        final int terminalVelocity
    ) {
        return withY(min(y + fallRate, terminalVelocity));
    }

    public boolean movingRight() {
        return x > 0;
    }

    public boolean movingLeft() {
        return x < 0;
    }

    public boolean stationaryX() {
        return x == 0;
    }

    public boolean descending() {
        return y >= 0;
    }

    public boolean rising() {
        return y < 0;
    }
}
