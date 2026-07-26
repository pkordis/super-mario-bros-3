package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
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

    @Getter
    private final Offset offset = Offset.of(0, 0);

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
    }
}
