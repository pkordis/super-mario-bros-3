package house.x1337.app.smb3.game.object.level.block.animator;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.NOTE_BLOCK_NO_REWARD;

/**
 * Animates the note blocks. Like the P-Switch, they have three distinct images played A-B-C-B, so {@code frame_3.png}
 * is a copy of {@code frame_1.png}. While a P-Switch is active they stand still on the first frame.
 */
@Getter
@Singleton
public final class NoteBlockAnimator extends GameObjectAnimatorSingleTiled<Block> {
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
    private final List<LevelObjectType> supportedTypes = List.of(
        NOTE_BLOCK_NO_REWARD
    );
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/block/note/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    private boolean frozenOnFirstFrame;

    @Override
    public void update() {
        repaintOnWindowChange();
        super.update();
    }

    @Override
    public void updateWhileHalted() {
        repaintOnWindowChange();
    }

    @Override
    protected int[] getFramePixels(final int frameIndex) {
        return super.getFramePixels(powerSwitchTimeWindow.isActive() ? 0 : frameIndex);
    }

    private void repaintOnWindowChange() {
        if (frozenOnFirstFrame != powerSwitchTimeWindow.isActive()) {
            frozenOnFirstFrame = powerSwitchTimeWindow.isActive();
            repaintCurrentFrame();
        }
    }
}
