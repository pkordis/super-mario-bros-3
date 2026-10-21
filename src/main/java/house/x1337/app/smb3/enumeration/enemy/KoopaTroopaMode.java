package house.x1337.app.smb3.enumeration.enemy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KoopaTroopaMode implements EnemyMode {
    GREEN("green"),
    RED("red");

    private final String framesFolder;
}
