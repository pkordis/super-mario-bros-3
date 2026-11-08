package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.model.game.Coordinates;

public interface BlockMotionManager extends MotionManager {
    boolean isBlockBumpActiveAt(Coordinates cell);
}
