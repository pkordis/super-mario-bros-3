package house.x1337.app.smb3.model.game;

import house.x1337.app.smb3.annotation.Prototype;

import static java.lang.Math.round;

@Prototype
public interface Offset {
    int x();
    int y();

    default boolean equals(final Offset offset) {
        return x() == offset.x() && y() == offset.y();
    }

    static Offset of(final int x, final int y) {
        return new GenericOffset(x, y);
    }

    static Offset of(final float x, final float y) {
        return of(round(x), round(y));
    }

    record GenericOffset(int x, int y) implements Offset {
    }
}
