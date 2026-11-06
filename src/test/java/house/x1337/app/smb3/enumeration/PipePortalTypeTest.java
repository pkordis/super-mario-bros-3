package house.x1337.app.smb3.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static org.assertj.core.api.Assertions.assertThat;

class PipePortalTypeTest {
    @Test
    @DisplayName("Only IN and IN_AND_OUT portals can be entered")
    void enterableTypes() {
        // Execute & Verify
        assertThat(IN.isEnterable()).isTrue();
        assertThat(IN_AND_OUT.isEnterable()).isTrue();
        assertThat(OUT.isEnterable()).isFalse();
    }

    @Test
    @DisplayName("Only OUT and IN_AND_OUT portals can be exited")
    void exitableTypes() {
        // Execute & Verify
        assertThat(OUT.isExitable()).isTrue();
        assertThat(IN_AND_OUT.isExitable()).isTrue();
        assertThat(IN.isExitable()).isFalse();
    }

    @Test
    @DisplayName("Every pipe portal type has a label")
    void everyTypeHasALabel() {
        // Execute & Verify
        assertThat(IN.getLabel()).isEqualTo("In");
        assertThat(OUT.getLabel()).isEqualTo("Out");
        assertThat(IN_AND_OUT.getLabel()).isEqualTo("In and Out");
    }
}
