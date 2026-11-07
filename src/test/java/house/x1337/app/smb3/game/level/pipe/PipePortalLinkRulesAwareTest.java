package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static org.assertj.core.api.Assertions.assertThat;

class PipePortalLinkRulesAwareTest {
    private final PipePortalLinkRulesAware rules = new PipePortalLinkRulesAware() {};

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource({
        "IN, IN, false",
        "IN, OUT, true",
        "IN, IN_AND_OUT, true",
        "OUT, IN, true",
        "OUT, OUT, false",
        "OUT, IN_AND_OUT, true",
        "IN_AND_OUT, IN, true",
        "IN_AND_OUT, OUT, true",
        "IN_AND_OUT, IN_AND_OUT, true"
    })
    @DisplayName("Two portals can be linked when the pair has a way in and a way out")
    void linkableTypes(final PipePortalType first, final PipePortalType second, final boolean expected) {
        // Execute
        final boolean linkable = rules.isLinkable(first, second);

        // Verify
        assertThat(linkable).isEqualTo(expected);
    }

    @Test
    @DisplayName("A missing type is never linkable")
    void missingTypeIsNotLinkable() {
        // Execute & Verify
        assertThat(rules.isLinkable(null, OUT)).isFalse();
        assertThat(rules.isLinkable(IN, null)).isFalse();
        assertThat(rules.isLinkable(null, null)).isFalse();
    }

    @ParameterizedTest(name = "entering {0} linked to {1} -> {2}")
    @CsvSource({
        "IN, OUT, true",
        "OUT, IN, false",
        "IN, IN_AND_OUT, true",
        "IN_AND_OUT, IN, false",
        "IN_AND_OUT, OUT, true",
        "OUT, IN_AND_OUT, false",
        "IN_AND_OUT, IN_AND_OUT, true"
    })
    @DisplayName("Entering a linked portal leads to its partner only when the partner can be come out of")
    void destinationByDirection(final PipePortalType entered, final PipePortalType linked, final boolean expected) {
        // Prepare
        final PipePortal enteredPortal = PipePortal.builder().id("a").type(entered).linksToId("b").build();
        final PipePortal linkedPortal = PipePortal.builder().id("b").type(linked).linksToId("a").build();

        // Execute & Verify
        assertThat(rules.destinationOf(enteredPortal, linkedPortal).isPresent()).isEqualTo(expected);
        rules.destinationOf(enteredPortal, linkedPortal)
            .ifPresent(destination -> assertThat(destination).isSameAs(linkedPortal));
    }

    @Test
    @DisplayName("A partner that does not point back is not a destination")
    void nonReciprocalLinkLeadsNowhere() {
        // Prepare
        final PipePortal entered = PipePortal.builder().id("a").type(IN).linksToId("b").build();
        final PipePortal linked = PipePortal.builder().id("b").type(OUT).linksToId("c").build();

        // Execute & Verify
        assertThat(rules.destinationOf(entered, linked)).isEmpty();
    }

    @Test
    @DisplayName("A free portal leads nowhere")
    void missingPartnerLeadsNowhere() {
        // Prepare
        final PipePortal entered = PipePortal.builder().id("a").type(IN_AND_OUT).build();

        // Execute & Verify
        assertThat(rules.destinationOf(entered, null)).isEmpty();
    }
}
