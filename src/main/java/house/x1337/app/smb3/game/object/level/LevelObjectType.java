package house.x1337.app.smb3.game.object.level;

public interface LevelObjectType {
    String getLabel();
    Class<? extends LevelObject> getInstanceType();
    String name();
    boolean isSingleTiled();

    default boolean isMultiTiled() {
        return !isSingleTiled();
    }
}
