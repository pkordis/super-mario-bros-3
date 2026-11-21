package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.game.object.level.block.animation.NoteBlockBounceAnimation;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;

import java.util.Optional;

public record Bounce(
    NoteBlockBounceAnimation animation,
    Optional<LevelScenePlayer> rider
) {
    public boolean isAt(final Coordinates offset) {
        return animation.getOffset().equals(offset);
    }
}
