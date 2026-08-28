package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerOrientationVertical;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlayerOrientation {
    private PlayerOrientationHorizontal horizontal;
    private PlayerOrientationVertical vertical;
}
