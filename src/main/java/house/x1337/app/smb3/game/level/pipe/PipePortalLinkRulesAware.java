package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.model.game.portal.PipePortal;

import java.util.Objects;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;

public interface PipePortalLinkRulesAware {
    default boolean isLinkable(final PipePortalType first, final PipePortalType second) {
        if (first == null || second == null) {
            return false;
        }
        return !(first == IN && second == IN) && !(first == OUT && second == OUT);
    }

    default Optional<PipePortal> destinationOf(final PipePortal entered, final PipePortal linked) {
        if (entered == null || linked == null || entered.getType() == null || linked.getType() == null) {
            return Optional.empty();
        }
        if (!entered.getType().isEnterable() || !linked.getType().isExitable()) {
            return Optional.empty();
        }
        if (!Objects.equals(linked.getLinksToId(), entered.getId())) {
            return Optional.empty();
        }
        return Optional.of(linked);
    }
}
