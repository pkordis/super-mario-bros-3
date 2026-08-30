package house.x1337.app.smb3.enumeration.resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RewardImageResource implements EnumeratedImageResourceType {
    SCORE_100("classpath:/sprites/reward/score/score_100.png"),
    SCORE_1000("classpath:/sprites/reward/score/score_1000.png");

    private final String path;
}
