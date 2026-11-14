package house.x1337.app.smb3.ui.editor.level.menu.scene;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LevelSceneSizeRulesTest {
    private final LevelSceneSizeRules rules = new LevelSceneSizeRules();

    @ParameterizedTest(name = "{0} columns by {1} rows")
    @CsvSource({
        "16, 15",
        "65535, 15",
        "16, 62500",
        "1000, 1000",
        "40000, 25"
    })
    @DisplayName("Sizes from the screen up to 65535 a side and a million tiles are allowed")
    void allowed(final int columns, final int rows) {
        // Execute & Verify
        assertThat(rules.validate(columns, rows)).isEmpty();
    }

    @ParameterizedTest(name = "{0} columns by {1} rows: {2}")
    @CsvSource({
        "15, 15, at least 16 columns by 15 rows",
        "16, 14, at least 16 columns by 15 rows",
        "65536, 15, at most 65535 tiles on each side",
        "16, 65536, at most 65535 tiles on each side",
        "1001, 1000, at most 1000000 tiles",
        "65535, 65535, at most 1000000 tiles"
    })
    @DisplayName("Sizes below the screen, over 65535 a side or over a million tiles are refused with the reason")
    void refused(final int columns, final int rows, final String reason) {
        // Execute
        final Optional<String> refusal = rules.validate(columns, rows);

        // Verify
        assertThat(refusal).hasValueSatisfying(text -> assertThat(text).contains(reason));
    }
}
