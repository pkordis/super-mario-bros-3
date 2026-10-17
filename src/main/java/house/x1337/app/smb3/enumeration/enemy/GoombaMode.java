package house.x1337.app.smb3.enumeration.enemy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GoombaMode implements EnemyMode {
    NORMAL("normal"),
    RED("red");

    private final String framesFolder;
}
