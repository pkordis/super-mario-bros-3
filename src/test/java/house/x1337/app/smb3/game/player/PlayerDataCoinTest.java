package house.x1337.app.smb3.game.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counting coins: the hundredth is traded for an extra life, and only that one says so.
 */
class PlayerDataCoinTest {
    @Test
    @DisplayName("Only the hundredth coin reports an extra life, and the count starts over after it")
    void onlyTheHundredthCoinEarnsALife() {
        // Prepare
        final PlayerData playerData = new PlayerData();
        final int livesBefore = playerData.getLives();
        final List<Integer> lifeEarningCoins = new ArrayList<>();

        // Execute
        for (int coin = 1; coin <= 150; coin++) {
            if (playerData.addCoin()) {
                lifeEarningCoins.add(coin);
            }
        }

        // Verify
        assertThat(lifeEarningCoins).containsExactly(100);
        assertThat(playerData.getLives()).isEqualTo(livesBefore + 1);
        assertThat(playerData.getCoins()).isEqualTo(50);
    }
}
