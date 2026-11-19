package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.SlopeProfile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.DUMMY_SOLID_OBJECT;

@Getter
@RequiredArgsConstructor
public final class RampLevelObject implements LevelObject {
    private final LevelObjectType type = DUMMY_SOLID_OBJECT;
    private final GameEngine gameEngine;
    private final Coordinates offset;
    private final SlopeProfile slopeProfile;

    @Override
    public Optional<SlopeProfile> findSlopeProfile() {
        return Optional.of(slopeProfile);
    }
}
