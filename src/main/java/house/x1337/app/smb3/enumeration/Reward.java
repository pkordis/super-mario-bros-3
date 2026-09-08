package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.resource.RewardImageResource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Slf4j
public enum Reward {
    ONE_UP,
    SCORE_10,
    SCORE_50,
    SCORE_100,
    SCORE_1000;

    @Getter(lazy = true)
    private final Data data = initData();

    private Data initData() {
        final Data rewardData = getBean(Data.class);
        try {
            rewardData.imageResource = RewardImageResource.valueOf(this.name());
        } catch (final IllegalArgumentException e) {
            log.warn("No image found for reward's score: {}", this.name());
        }
        try {
            rewardData.points = Integer.parseInt(this.name().replace("SCORE_", ""));
            rewardData.lives = 0;
        } catch (final NumberFormatException e) {
            rewardData.points = 0;
            if ("ONE_UP".equalsIgnoreCase(this.name())) {
                rewardData.lives = 1;
            }
        }
        return rewardData;
    }

    @Getter
    @Prototype
    public static class Data {
        private RewardImageResource imageResource;
        private int points;
        private int lives;
    }
}
