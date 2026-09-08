package house.x1337.app.smb3.util;

public interface EnumValuesMatcher<E extends Enum<E>> {
    @SuppressWarnings("unchecked")
    default boolean isNoneOf(final E... es) {
        for (final E e : es) {
            if (this.equals(e)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    default boolean isAnyOf(final E... es) {
        return !isNoneOf(es);
    }
}
