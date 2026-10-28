package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.enumeration.Reward;

import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_200;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_2000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_400;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_4000;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_800;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_8000;
import static java.lang.Math.clamp;
import static java.lang.Math.min;

public interface KillTallyAware {
    Reward[] KILL_TALLY_LADDER = {
        SCORE_100,
        SCORE_200,
        SCORE_400,
        SCORE_800,
        SCORE_1000,
        SCORE_2000,
        SCORE_4000,
        SCORE_8000,
        ONE_UP
    };
    int MAX_KILL_TALLY = KILL_TALLY_LADDER.length - 1;

    int getKillTally();

    default int nextKillTally() {
        return min(getKillTally() + 1, MAX_KILL_TALLY);
    }

    default Reward resolveKillReward() {
        return KILL_TALLY_LADDER[clamp(getKillTally(), 0, KILL_TALLY_LADDER.length - 1)];
    }
}
