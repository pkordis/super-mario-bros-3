package house.x1337.app.smb3.enumeration.enemy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PiranhaPlantMode implements EnemyMode {
    GREEN("green", 25, 24),
    RED("red", 33, 20);

    private final String framesFolder;
    private final int riseDistancePixels;
    private final int boundBoxHeightPixels;
}
