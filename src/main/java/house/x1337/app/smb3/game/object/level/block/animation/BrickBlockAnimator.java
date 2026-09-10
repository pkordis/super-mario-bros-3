package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.ImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.BRICK_BLOCK_NO_REWARD;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.BRICK_BLOCK_WITH_REWARD;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.BRICK_SWITCH_BLOCK_SPAWNER;

@Getter
@Singleton
public final class BrickBlockAnimator extends GameObjectAnimatorSingleTiled<Block> {
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
    private final List<LevelObjectType> supportedTypes = List.of(
        BRICK_BLOCK_NO_REWARD,
        BRICK_BLOCK_WITH_REWARD,
        BRICK_SWITCH_BLOCK_SPAWNER
    );
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/brick/plain/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    @Value("classpath:/sprites/object/coin/flipping/frame_0.png")
    private ImageResource powerSwitchedCoinImageResource;

    private boolean substituting;

    @Override
    public void update() {
        // Repaint the moment the window opens or closes rather than waiting up to 8 ticks for the next
        // frame flip; the ROM's substitution happens inside the tile lookup, so it is instantaneous.
        if (substituting != powerSwitchTimeWindow.isActive()) {
            substituting = powerSwitchTimeWindow.isActive();
            repaintCurrentFrame();
        }
        super.update();
    }

    @Override
    protected int[] getFramePixels(final int frameIndex) {
        return powerSwitchTimeWindow.isActive()
            ? powerSwitchedCoinImageResource.getRgbData()
            : super.getFramePixels(frameIndex);
    }
}
