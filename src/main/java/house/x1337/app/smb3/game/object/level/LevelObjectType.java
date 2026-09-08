package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.level.variant.ConfigurableVariant;

public interface LevelObjectType {
    String getLabel();
    Class<? extends LevelObject> getInstanceType();
    ConfigurableVariant.VariantData getVariantData();
    String name();
    boolean isSingleTiled();

    default boolean isMultiTiled() {
        return !isSingleTiled();
    }
}
