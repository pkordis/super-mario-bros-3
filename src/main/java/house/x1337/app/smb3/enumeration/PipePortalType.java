package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.util.EnumValuesMatcher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PipePortalType implements EnumValuesMatcher<PipePortalType> {
    IN("In"),
    OUT("Out"),
    IN_AND_OUT("In and Out");

    private final String label;

    public boolean isEnterable() {
        return isAnyOf(IN, IN_AND_OUT);
    }

    public boolean isExitable() {
        return isAnyOf(OUT, IN_AND_OUT);
    }
}
