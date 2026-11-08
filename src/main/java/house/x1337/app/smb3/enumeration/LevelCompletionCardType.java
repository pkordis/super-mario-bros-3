package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    private final String levelSpritePath;
    private final String statusBarPath;
}
