package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.object.level.variant.ConfigurableVariant;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.variant.LevelObjectVariantsAware;
import house.x1337.app.smb3.game.object.level.SolidLevelObject;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.game.object.level.block.EmptyBlock;
import house.x1337.app.smb3.game.object.level.reward.Coin;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LevelObjectTypeSingleTiled implements LevelObjectType, LevelObjectVariantsAware {
    // Dummy Objects
    DUMMY_SOLID_OBJECT("Dummy Solid Object", SolidLevelObject.class, V_NONE),

    // Blocks
    BRICK_BLOCK_NO_REWARD("Brick Block (breakable - no reward)", Block.class, V_NONE),
    BRICK_BLOCK_WITH_REWARD("Brick Block with Reward (non-breakable)", Block.class, V_NONE),
    EMPTY_BLOCK("Empty Block (solid/used)", EmptyBlock.class, V_NONE),
    QUESTION_BLOCK("Question Block", Block.class, V_NONE),

    // Rewards
    COIN_FLIPPING("Coin", Coin.class, V_NONE),
    ONE_UP_MUSHROOM("1UP Mushroom", SuperMushroom.class, V_LIFE_AWARDING_GREEN),
    SUPER_LEAF("Super Leaf", SuperLeaf.class, V_GROWING_CAPABLE),
    SUPER_MUSHROOM("Super Mushroom", SuperMushroom.class, V_GROWING_CAPABLE);

    private final boolean singleTiled = true;
    private final String label;
    private final Class<? extends LevelObject> instanceType;
    private final ConfigurableVariant.VariantData variantData;
}
