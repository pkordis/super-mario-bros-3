package house.x1337.app.smb3.enumeration.resource;

import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LevelCompletionCardImageResource implements EnumeratedImageResourceType {
    MUSHROOM(LevelCompletionCardType.MUSHROOM),
    FLOWER(LevelCompletionCardType.FLOWER),
    STAR(LevelCompletionCardType.STAR);

    private final LevelCompletionCardType card;

    @Override
    public String getPath() {
        return card.getLevelSpritePath();
    }
}
