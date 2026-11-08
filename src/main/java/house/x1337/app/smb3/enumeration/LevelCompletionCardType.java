package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.enumeration.resource.LevelCompletionCardImageResource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum LevelCompletionCardType {
    MUSHROOM(
        "classpath:/sprites/object/card/level_completion/mushroom.png",
        "classpath:/hud/card/mushroom.png"
    ),
    FLOWER(
        "classpath:/sprites/object/card/level_completion/flower.png",
        "classpath:/hud/card/flower.png"
    ),
    STAR(
        "classpath:/sprites/object/card/level_completion/star.png",
        "classpath:/hud/card/star.png"
    );

    @Getter(lazy = true)
    @Accessors(fluent = true)
    private final LevelCompletionCardImageResource asResource = initResource();

    private final String levelSpritePath;
    private final String statusBarPath;

    private LevelCompletionCardImageResource initResource() {
        return Arrays.stream(LevelCompletionCardImageResource.values())
            .filter(r -> r.getCard() == this)
            .findFirst()
            .orElse(null);
    }
}
