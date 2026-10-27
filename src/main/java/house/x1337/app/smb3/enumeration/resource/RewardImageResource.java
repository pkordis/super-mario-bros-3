package house.x1337.app.smb3.enumeration.resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RewardImageResource implements EnumeratedImageResourceType {
    ONE_UP("classpath:/sprites/reward/life/1up.png"),
    SCORE_100("classpath:/sprites/reward/score/score_100.png"),
    SCORE_200("classpath:/sprites/reward/score/score_200.png"),
    SCORE_400("classpath:/sprites/reward/score/score_400.png"),
    SCORE_800("classpath:/sprites/reward/score/score_800.png"),
    SCORE_1000("classpath:/sprites/reward/score/score_1000.png"),
    SCORE_2000("classpath:/sprites/reward/score/score_2000.png"),
    SCORE_4000("classpath:/sprites/reward/score/score_4000.png"),
    SCORE_8000("classpath:/sprites/reward/score/score_8000.png");

    private final String path;
}
