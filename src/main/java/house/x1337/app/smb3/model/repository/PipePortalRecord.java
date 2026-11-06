package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Id;

import java.io.Serializable;

@Entity(
    value = "pipePortals"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public final class PipePortalRecord implements Serializable {
    @Id
    private String id;
    private String description;
    private PipePortalType type;
    private VirtualObjectType virtualObjectType;
    private String levelSceneId;
    private int row;
    private int column;
}
