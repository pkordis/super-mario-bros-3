package house.x1337.app.smb3.game.level.completion;

import org.springframework.core.io.DefaultResourceLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;

/** A square grid of characters read from a text file on the classpath, one row per line, top row first. */
interface CharacterGridResource {
    default String[] readGrid(
        final String path,
        final int size,
        final String characters
    ) {
        final List<String> rows = readLines(path);
        if (rows.size() != size) {
            throw new IllegalStateException(path + " has " + rows.size() + " rows, not " + size);
        }
        for (int row = 0; row < size; row++) {
            final String cells = rows.get(row);
            if (cells.length() != size) {
                throw new IllegalStateException(path + ": row " + row + " has " + cells.length() + " cells, not "
                    + size);
            }
            for (int column = 0; column < size; column++) {
                if (characters.indexOf(cells.charAt(column)) < 0) {
                    throw new IllegalStateException(path + ": row " + row + ", column " + column + " holds '"
                        + cells.charAt(column) + "', not one of \"" + characters + "\"");
                }
            }
        }
        return rows.toArray(String[]::new);
    }

    private List<String> readLines(final String path) {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
            new DefaultResourceLoader().getResource(path).getInputStream(),
            US_ASCII
        ))) {
            final List<String> lines = reader.lines().toList();
            int last = lines.size();
            while (last > 0 && lines.get(last - 1).isBlank()) {
                last--;
            }
            return lines.subList(0, last);
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
