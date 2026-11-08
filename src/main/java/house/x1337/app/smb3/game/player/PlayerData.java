package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.NONE;

@Data
@Prototype
public final class PlayerData {
    public static final int CARD_SLOTS = 3;

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

    @Getter(NONE)
    private final LevelCompletionCardType[] cards = new LevelCompletionCardType[CARD_SLOTS];

    public int getTimer() {
        return playerTimer.getTime();
    }

    public boolean isTimerShown() {
        return playerTimer.isShown();
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

    public List<LevelCompletionCardType> getCards() {
        return Collections.unmodifiableList(Arrays.asList(cards.clone()));
    }

    public void addCard(final LevelCompletionCardType card) {
        requireNonNull(card, "card");
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            if (cards[slot] == null) {
                cards[slot] = card;
                return;
            }
        }
        System.arraycopy(cards, 1, cards, 0, CARD_SLOTS - 1);
        cards[CARD_SLOTS - 1] = card;
    }

    public void clearCards() {
        Arrays.fill(cards, null);
    }

    @Data
    @AllArgsConstructor
    public static class State {
        private int time;
        private int score;
        private int coins;
        private int lives;
        private int world;
        private List<LevelCompletionCardType> cards;

        public boolean equals(final PlayerData playerData) {
            return playerData.score == this.score &&
                playerData.coins == this.coins &&
                playerData.lives == this.lives &&
                playerData.world == this.world &&
                playerData.getPlayerTimer().getTime() == this.time &&
                Objects.equals(playerData.getCards(), this.cards);
        }

        @Override
        public int hashCode() {
            return hash(time, score, coins, lives, world, cards);
        }

        public void updateFrom(final PlayerData snapshot) {
            score = snapshot.score;
            coins = snapshot.coins;
            lives = snapshot.lives;
            world = snapshot.world;
            time = snapshot.getPlayerTimer().getTime();
            cards = snapshot.getCards();
        }
    }
}
