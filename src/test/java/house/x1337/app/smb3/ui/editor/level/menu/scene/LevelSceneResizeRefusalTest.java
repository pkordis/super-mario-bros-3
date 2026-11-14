package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.model.game.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;

class LevelSceneResizeRefusalTest {
    @Test
    @DisplayName("The refusal lists each group under its heading, one cell per line as the status bar shows it")
    void listsCellsPerGroup() {
        // Prepare
        final Map<String, List<Coordinates>> cut = new LinkedHashMap<>();
        cut.put("Static Environment", List.of(of(3, 0), of(17, 4)));
        cut.put("Spawn Point", List.of(of(2, 1)));

        // Execute
        final String text = new LevelSceneResizeRefusal().describe(cut);

        // Verify
        assertThat(text).isEqualTo("""
            Static Environment
              col=3  row=0
              col=17  row=4

            Spawn Point
              col=2  row=1
            """);
    }
}
