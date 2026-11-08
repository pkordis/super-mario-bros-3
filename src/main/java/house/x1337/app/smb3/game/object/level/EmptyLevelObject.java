package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.variant.ConfigurableVariant;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;

import static house.x1337.app.smb3.model.game.Coordinates.of;

public final class EmptyLevelObject implements LevelObject {
    @Getter
    private final LevelObjectType type = new LevelObjectType() {
        @Override
        public String getLabel() {
            return "<empty>";
        }

        @Override
        public Class<? extends LevelObject> getInstanceType() {
            return EmptyLevelObject.class;
        }

        @Override
        public ConfigurableVariant.VariantData getVariantData() {
            return null;
        }

        @Override
        public String name() {
            return "EMPTY";
        }

        @Override
        public boolean isSingleTiled() {
            return true;
        }
    };

    @Getter
    private final Coordinates offset = of(0, 0);

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public GameEngine getGameEngine() {
        return null;
    }
}
