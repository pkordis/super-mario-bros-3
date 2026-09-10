package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import lombok.AllArgsConstructor;
import lombok.Data;

import static java.util.Objects.hash;

@Data
@Prototype
public final class PlayerData {
    private PlayerIdentity identity;
    private int playerPower;
    private int powerThrottle;
    private int powerMeter = 0;
    private boolean powerMeterFull = false;
    private int score = 0;
    private int coins = 0;
    private int lives = 4;
    private int world = 1;
    private final PlayerTimer playerTimer = new PlayerTimer();

    public int getTimer() {
        return playerTimer.getTime();
    }

    public boolean hasTimerActive() {
        return playerTimer.isActive();
    }

    public void addCoin() {
        ++coins;

        if (coins == 100) {
            coins -= 100;
            addLives(1);
        }
    }

    public void addPoints(final Integer score) {
        this.score += score;
    }

    public void addLives(final Integer lives) {
        this.lives += lives;
    }

    public void add(final Reward.Data data) {
        addPoints(data.getPoints());
        addLives(data.getLives());
    }

    @Data
    @AllArgsConstructor
    public static class State {
        private int time;
        private int score;
        private int coins;
        private int lives;
        private int world;

        public boolean equals(final PlayerData playerData) {
            return playerData.score == this.score &&
                playerData.coins == this.coins &&
                playerData.lives == this.lives &&
                playerData.world == this.world &&
                playerData.getPlayerTimer().getTime() == this.time;
        }

        @Override
        public int hashCode() {
            return hash(time, score, coins, lives, world);
        }

        public void updateFrom(final PlayerData snapshot) {
            score = snapshot.score;
            coins = snapshot.coins;
            lives = snapshot.lives;
            world = snapshot.world;
            time = snapshot.getPlayerTimer().getTime();
        }
    }
}
