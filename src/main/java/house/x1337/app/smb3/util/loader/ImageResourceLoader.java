package house.x1337.app.smb3.util.loader;

import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.service.TileService;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ImageResource.fromLazilyLoadedData;

public interface ImageResourceLoader {
    default ImageResource loadForLevelObjectType(final LevelObjectTypeSingleTiled levelObjectType) {
        return fromLazilyLoadedData(
            () -> {
                final Integer id = getBean(LevelObjectService.class)
                    .getLevelObjectIdOfType(levelObjectType)
                    .orElseThrow();
                return getBean(TileService.class)
                    .findById(id)
                    .orElseThrow()
                    .getArgbData();
            },
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE
        );
    };
}
