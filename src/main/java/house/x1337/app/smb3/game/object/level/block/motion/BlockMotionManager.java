package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.model.game.Offset;

public interface BlockMotionManager<L extends LevelObject> extends MotionManager<L> {
    boolean isBlockBumpActiveAt(Offset cell);
}
