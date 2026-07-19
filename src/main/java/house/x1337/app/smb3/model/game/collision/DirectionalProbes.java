package house.x1337.app.smb3.model.game.collision;

/**
 * Groups four {@link CollisionProbe} instances by movement direction,
 * eliminating array indexing in favor of named accessors.
 */
public record DirectionalProbes(
    CollisionProbe upLeft,
    CollisionProbe upRight,
    CollisionProbe downLeft,
    CollisionProbe downRight
) {

    public CollisionProbe resolve(
        final boolean movingUp,
        final boolean leftHalf
    ) {
        if (movingUp) {
            return leftHalf ? upLeft : upRight;
        }
        return leftHalf ? downLeft : downRight;
    }
}
