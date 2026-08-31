package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.SolidLevelObject;
import house.x1337.app.smb3.game.object.level.block.QuestionBlock;
import house.x1337.app.smb3.game.object.level.brick.BrickBlockWithReward;
import house.x1337.app.smb3.game.object.level.brick.BrickBlockWithoutReward;
import house.x1337.app.smb3.game.object.level.block.EmptyBlock;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LevelObjectTypeSingleTiled implements LevelObjectType {
    // Dummy Objects
    DUMMY_SOLID_OBJECT("Dummy Solid Object", SolidLevelObject.class),

    // Blocks
    BRICK_BLOCK_NO_REWARD("Brick Block (breakable - no reward)", BrickBlockWithoutReward.class),
    BRICK_BLOCK_WITH_REWARD("Brick Block with Reward (non-breakable)", BrickBlockWithReward.class),
    EMPTY_BLOCK("Empty Block (solid/used)", EmptyBlock.class),
    QUESTION_BLOCK("Question Block", QuestionBlock.class),

    // Rewards
    SUPER_LEAF("Super Leaf", SuperLeaf.class),
    SUPER_MUSHROOM("Super Mushroom", SuperMushroom.class);

    private final boolean singleTiled = true;
    private final String label;
    private final Class<? extends LevelObject> instanceType;
}
