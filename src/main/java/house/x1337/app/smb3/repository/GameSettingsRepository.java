package house.x1337.app.smb3.repository;

import java.util.Map;
import java.util.Optional;

/**
 * Stores the game settings as one record, {@code settings}, in the {@code gameData} collection, one field per setting.
 * The record's identifying field is left out of what is read and written here.
 */
public interface GameSettingsRepository {
    String RECORD_ID = "settings";

    /** @return the saved fields of the settings record, or nothing when no record has been saved */
    Optional<Map<String, Object>> load();

    /** Replaces the settings record with exactly these fields, creating it if needed. */
    void save(Map<String, Object> fields);
}
