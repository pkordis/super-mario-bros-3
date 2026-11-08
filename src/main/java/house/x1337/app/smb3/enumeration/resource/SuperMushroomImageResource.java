package house.x1337.app.smb3.enumeration.resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuperMushroomImageResource implements EnumeratedImageResourceType {
    GROWING_CAPABLE("classpath:/sprites/reward/mushroom/mushroom_normal.png"),
    LIFE_AWARDING_GREEN("classpath:/sprites/reward/mushroom/mushroom_one_up_green.png");

    private final String path;
}
