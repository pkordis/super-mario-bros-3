package house.x1337.app.smb3.model.event;

import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** The end-level card has been touched; {@code threeOfAKind} when it made three matching cards. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LevelCardCollected extends GameEvent {
    private final LevelCompletionCardType card;
    private final boolean threeOfAKind;
}
