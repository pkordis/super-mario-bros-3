package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.util.EnumValuesMatcher;

/**
 * The flavors of the single {@link house.x1337.app.smb3.game.object.level.block.Block} class. Each
 * constant must have an identically named counterpart in {@link LevelObjectTypeSingleTiled} whose
 * instance type is {@code Block}, because that is how a block resolves its own level-object type.
 */
public enum BlockType implements EnumValuesMatcher<BlockType> {
    BRICK_BLOCK_NO_REWARD,
    BRICK_BLOCK_WITH_REWARD,
    BRICK_SWITCH_BLOCK_SPAWNER,
    QUESTION_BLOCK,
    QUESTION_SWITCH_BLOCK_SPAWNER
}
