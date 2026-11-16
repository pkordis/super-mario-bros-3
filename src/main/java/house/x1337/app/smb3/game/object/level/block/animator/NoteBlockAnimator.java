package house.x1337.app.smb3.game.object.level.block.animator;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.NOTE_BLOCK_NO_REWARD;

@Getter
@Singleton
public final class NoteBlockAnimator extends GameObjectAnimatorSingleTiled<Block> {
    private final List<LevelObjectType> supportedTypes = List.of(
        NOTE_BLOCK_NO_REWARD
    );
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/block/note/frame_0.png")
    private AnimationImageResource animationFrames;
}
