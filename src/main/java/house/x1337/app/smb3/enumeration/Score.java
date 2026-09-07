package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.resource.RewardImageResource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Slf4j
public enum Score {
    SCORE_10,
    SCORE_50,
    SCORE_100,
    SCORE_1000;

    @Getter(lazy = true)
    private final Data data = initData();

    private Data initData() {
        final Data scoreData = getBean(Data.class);
        try {
            scoreData.imageResource = RewardImageResource.valueOf(this.name());
        } catch (final IllegalArgumentException e) {
            log.warn("No image found for reward's score: {}", this.name());
        }
        scoreData.value = Integer.parseInt(this.name().replace("SCORE_", ""));
        return scoreData;
    }

    @Getter
    @Prototype
    public static class Data {
        private RewardImageResource imageResource;
        private int value;

    }
}
