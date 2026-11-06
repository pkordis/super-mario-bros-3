package house.x1337.app.smb3.model.game.portal;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@Prototype
@NoArgsConstructor
@AllArgsConstructor
public final class PipePortal implements PipePortalCapabilities {
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String description;
    private PipePortalType type;
    private VirtualObjectType virtualObjectType;
    private String levelSceneId;
    private int row;
    private int column;

    public record Cell(int row, int column) {
    }
}
