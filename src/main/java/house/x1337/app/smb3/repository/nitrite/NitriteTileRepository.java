package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.repository.TileRepository;
import lombok.RequiredArgsConstructor;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.repository.ObjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import static org.dizitart.no2.filters.FluentFilter.where;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitriteTileRepository implements TileRepository {

    private final ObjectRepository<TileRecord> tileObjectRepository;

    @Override
    public void insert(final TileRecord record) {
        tileObjectRepository.insert(record);
    }

    @Override
    public Iterable<TileRecord> findAll() {
        return tileObjectRepository.find();
    }

    @Override
    public void updateMetadata(final int id, final TileType type, final String description, final int[] argbData) {
        final Document patch = Document.createDocument()
            .put("type", type != null ? type.name() : null)
            .put("description", description)
            .put("argbData", argbData);
        tileObjectRepository.getDocumentCollection()
            .update(where("id").eq(id), patch);
    }
}
