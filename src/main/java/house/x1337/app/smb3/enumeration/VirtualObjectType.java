package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.Color;

import static java.awt.Color.RED;

@Getter
@RequiredArgsConstructor
public enum VirtualObjectType {
    PIPE_PORTAL_HORIZONTAL(RED, 1, 2, "Pipe Portal (Horizontal)"),
    PIPE_PORTAL_VERTICAL(RED, 2, 1, "Pipe Portal (Vertical)");

    private final Color colour;
    private final int rows;
    private final int columns;
    private final String label;
}
