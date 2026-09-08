package house.x1337.app.smb3.game.object.level.variant;

import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuperMushroomVariantData implements ConfigurableVariant.VariantData {
    private final LevelObjectType type;
    private final SuperMushroomImageResource imageResourceType;
    private final Reward reward;

    public void applyTo(final SuperMushroom superMushroom) {
        superMushroom.setType(type);
        superMushroom.setImageResourceType(imageResourceType);
        superMushroom.setRewardType(reward);
    }
}
