package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCard;
import house.x1337.app.smb3.game.object.level.variant.ConfigurableVariant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VirtualLevelObjectType implements LevelObjectType {
    LEVEL_COMPLETION_CARD("Level Completion Card", LevelCompletionCard.class);

    private final boolean singleTiled = true;
    private final String label;
    private final Class<? extends LevelObject> instanceType;
    private final ConfigurableVariant.VariantData variantData = null;
}
