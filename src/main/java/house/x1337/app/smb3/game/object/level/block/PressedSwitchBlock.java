package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.PRESSED_SWITCH_BLOCK;

@Getter
@Prototype
@RequiredArgsConstructor
public class PressedSwitchBlock implements LevelObject {
    private final LevelObjectType type = PRESSED_SWITCH_BLOCK;
    private final GameEngine gameEngine;
    private final Offset offset;

    @Value("classpath:/sprites/object/block/switch/pressed.png")
    private ImageResource imageResource;

    @Override
    public boolean isCollidable() {
        return false;
    }
}
