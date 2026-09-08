package house.x1337.app.smb3.game.object.level.variant;

import house.x1337.app.smb3.game.object.level.variant.ConfigurableVariant.VariantData;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SUPER_MUSHROOM;
import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource.GROWING_CAPABLE;
import static house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource.LIFE_AWARDING_GREEN;

public interface LevelObjectVariantsAware {
    // General
    VariantData V_NONE = null;

    // Super Mushroom
    VariantData V_GROWING_CAPABLE = new SuperMushroomVariantData(SUPER_MUSHROOM, GROWING_CAPABLE, SCORE_1000);
    VariantData V_LIFE_AWARDING_GREEN = new SuperMushroomVariantData(SUPER_MUSHROOM, LIFE_AWARDING_GREEN, ONE_UP);
}
