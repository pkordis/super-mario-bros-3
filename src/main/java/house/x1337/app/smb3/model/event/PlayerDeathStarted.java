package house.x1337.app.smb3.model.event;

import house.x1337.app.smb3.enumeration.PlayerDeathKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerDeathStarted extends GameEvent {
    private final PlayerDeathKind kind;
}
