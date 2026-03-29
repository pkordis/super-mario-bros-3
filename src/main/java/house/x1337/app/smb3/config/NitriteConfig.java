package house.x1337.app.smb3.config;

import house.x1337.app.smb3.model.Sprite;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.mapper.jackson.JacksonMapperModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.repository.ObjectRepository;
import org.dizitart.no2.store.StoreModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NitriteConfig {
    @Bean
    StoreModule storeModule(
        @Value("${db.nitrite.file-path:./smb3.db}") final String filePath,
        @Value("${db.nitrite.compress:false}") final boolean compress
    ) {
        return MVStoreModule.withConfig()
            .filePath(filePath)
            .compress(compress)
            .build();
    }

    @Bean
    Nitrite nitrite(final StoreModule storeModule) {
        return Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new JacksonMapperModule())
            .openOrCreate("Mario", "1337");
    }

    @Bean
    ObjectRepository<Sprite> spriteRepository(final Nitrite nitrite) {
        return nitrite.getRepository(Sprite.class);
    }
}
