package house.x1337.app.smb3.game.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The player's three end-level card slots, filled left to right with the oldest card dropped once all three are
 * taken, and their part in the HUD's dirty snapshot.
 */
class PlayerDataCardsTest {
    @Test
    @DisplayName("A new player holds no cards")
    void aNewPlayerHoldsNoCards() {
        // Prepare
        final PlayerData playerData = new PlayerData();

        // Execute & Verify
        assertThat(playerData.getCards()).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("Cards fill the slots left to right")
    void cardsFillTheSlotsLeftToRight() {
        // Prepare
        final PlayerData playerData = new PlayerData();

        // Execute & Verify
        playerData.addCard(STAR);
        assertThat(playerData.getCards()).containsExactly(STAR, null, null);
        playerData.addCard(MUSHROOM);
        assertThat(playerData.getCards()).containsExactly(STAR, MUSHROOM, null);
        playerData.addCard(FLOWER);
        assertThat(playerData.getCards()).containsExactly(STAR, MUSHROOM, FLOWER);
    }

    @Test
    @DisplayName("A fourth card drops the first, shifts the others left and lands in the third slot")
    void aFourthCardRotatesIn() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        playerData.addCard(MUSHROOM);
        playerData.addCard(FLOWER);
        playerData.addCard(STAR);

        // Execute
        playerData.addCard(MUSHROOM);

        // Verify
        assertThat(playerData.getCards()).containsExactly(FLOWER, STAR, MUSHROOM);
    }

    @Test
    @DisplayName("Clearing the cards empties all three slots, and the next card goes into the first")
    void clearingEmptiesAllThreeSlots() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        playerData.addCard(MUSHROOM);
        playerData.addCard(FLOWER);
        playerData.addCard(STAR);

        // Execute
        playerData.clearCards();

        // Verify
        assertThat(playerData.getCards()).containsExactly(null, null, null);
        playerData.addCard(STAR);
        assertThat(playerData.getCards()).containsExactly(STAR, null, null);
    }

    @Test
    @DisplayName("The held cards are handed out as a copy that cannot change the slots")
    void theCardsAreACopy() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        playerData.addCard(MUSHROOM);

        // Execute & Verify
        assertThatThrownBy(() -> playerData.getCards().set(0, STAR))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(playerData.getCards()).containsExactly(MUSHROOM, null, null);
    }

    @Test
    @DisplayName("No card is not a card")
    void aNullCardIsRefused() {
        // Prepare
        final PlayerData playerData = new PlayerData();

        // Execute & Verify
        assertThatThrownBy(() -> playerData.addCard(null)).isInstanceOf(NullPointerException.class);
        assertThat(playerData.getCards()).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("A card change makes the HUD snapshot stale, and an unchanged player does not")
    void aCardChangeMarksTheHudDirty() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        final PlayerData.State snapshot = new PlayerData.State(-1, -1, -1, -1, -1, null);
        snapshot.updateFrom(playerData);

        // Execute & Verify
        assertThat(snapshot.equals(playerData)).as("Nothing changed since the snapshot").isTrue();

        playerData.addCard(FLOWER);
        assertThat(snapshot.equals(playerData)).as("A card was added").isFalse();

        snapshot.updateFrom(playerData);
        assertThat(snapshot.equals(playerData)).as("The snapshot caught up with the card").isTrue();

        playerData.addCard(FLOWER);
        playerData.addCard(FLOWER);
        snapshot.updateFrom(playerData);
        playerData.addCard(FLOWER);
        assertThat(playerData.getCards()).as("A rotation that leaves the same cards")
            .containsExactly(FLOWER, FLOWER, FLOWER);
        assertThat(snapshot.equals(playerData)).as("The same cards in the same slots").isTrue();

        playerData.clearCards();
        assertThat(snapshot.equals(playerData)).as("The cards were cleared").isFalse();
    }
}
