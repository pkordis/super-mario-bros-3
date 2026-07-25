package house.x1337.app.smb3.game.object.level;

import lombok.Getter;

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
        public String name() {
            return "EMPTY";
        }

        @Override
        public boolean isSingleTiled() {
            return true;
        }
    };

    @Override
    public boolean isCollidable() {
        return false;
    }
}
