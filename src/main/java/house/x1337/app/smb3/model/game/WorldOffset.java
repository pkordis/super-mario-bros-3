package house.x1337.app.smb3.model.game;

import house.x1337.app.smb3.annotation.Prototype;

@Prototype
public interface WorldOffset {
    float x();
    float y();
    float z();

    default WorldOffset plus(final float dx, final float dy, final float dz) {
        return of(
            x() + dx,
            y() + dy,
            z() + dz
        );
    }

    default boolean equals(final WorldOffset offset) {
        return x() == offset.x() && y() == offset.y()  && z() == offset.z();
    }

    static WorldOffset of(final float x, final float y, final float z) {
        return new GenericWorldOffset(x, y, z);
    }

    record GenericWorldOffset(float x, float y, float z) implements WorldOffset {
    }
}
