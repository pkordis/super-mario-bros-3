package house.x1337.app.smb3.util;

public interface CastCapable {
    @SuppressWarnings("unchecked")
    default <T> T checkedCast(Object o) {
        return (T) o;
    }
}
