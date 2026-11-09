package house.x1337.app.smb3.game.object.level.block.animator;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.SwitchBlock;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SWITCH_BLOCK;

/**
 * Shimmer animator for the P-Switch ({@link SwitchBlock}).
 *
 * <p>Every animated background tile is driven by one global animation cycle of four phases of 8 ticks
 * each, a 32-tick loop. That is why this animator, the question block and the brick all use four frames
 * at 8 ticks.
 *
 * <p>The P-Switch art is identical in the second and fourth phases, so the switch has only <b>three</b>
 * distinct images and they play as a ping-pong <b>A-B-C-B</b>: the middle frame is on screen for 16 of
 * the 32 ticks (50%) while the outer two get 8 ticks each (25%). {@code frame_3.png} is therefore an
 * intentional duplicate of {@code frame_1.png}, which lets a plain linear four-frame cycle reproduce the
 * ping-pong timing exactly.
 */
@Getter
@Singleton
public final class SwitchBlockAnimator extends GameObjectAnimatorSingleTiled<SwitchBlock> {
    private final List<LevelObjectType> supportedTypes = List.of(SWITCH_BLOCK);
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/block/switch/frame_{0,3}.png")
    private AnimationImageResource animationFrames;
}
