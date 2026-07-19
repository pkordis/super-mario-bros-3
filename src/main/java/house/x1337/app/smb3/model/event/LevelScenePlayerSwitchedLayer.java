package house.x1337.app.smb3.model.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class LevelScenePlayerSwitchedLayer extends GameEvent {
    private String inputHandlerId;
}
