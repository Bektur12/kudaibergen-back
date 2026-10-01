package kg.kudaibergen.garage;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.DictionaryVersion;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.dto.ModelDto;
import kg.kudaibergen.garage.dto.ModelSearchItem;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.media.MediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Справочник марок и моделей. Открыт гостям: нужен каталогу и карте без входа. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Справочник авто")
public class VehicleDirectoryController {

   private static final int MODEL_SEARCH_LIMIT = 30;

   private final VehicleDirectory directory;
   private final MediaService media;
   private final DictionaryVersion version;

   public VehicleDirectoryController(VehicleDirectory directory, MediaService media, DictionaryVersion version) {
      this.directory = directory;
      this.media = media;
      this.version = version;
   }

   @GetMapping("/dictionaries/version")
   @Operation(summary = "Версия справочников", description = "Растёт при любой правке марок, моделей, категорий, "
         + "услуг и подсказок в админке: изменилась — перекачать справочники")
   public DictionaryVersion.Current dictionariesVersion() {
      return version.current();
   }

   @GetMapping("/brands/{id}/logo")
   @Operation(summary = "Логотип марки, загруженный в админке", description = "Редирект на файл; ссылка постоянная")
   public ResponseEntity<Void> logo(@PathVariable Long id) {
      Brand brand = directory.brand(id);
      String url = brand.getLogoMediaId() == null ? null : media.thumbUrl(brand.getLogoMediaId());
      if (url == null) {
         throw new NotFoundException("LOGO_NOT_FOUND", "У марки нет загруженного логотипа");
      }
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1))).build();
   }

   @GetMapping("/brands")
   @Operation(summary = "Марки", description = """
         Сначала 8 популярных (popular=true, порядок плиток), затем остальные по алфавиту.
         q — поиск по марке или модели: «мерс», «бэха», «камри» (экраны 23, 28).""")
   public List<BrandDto> brands(@RequestParam(required = false) String q,
                                @RequestParam(required = false) Boolean popular) {
      List<Brand> found = q == null || q.isBlank() ? directory.brands() : directory.searchBrands(q);
      return found.stream()
            .filter(Brand::isActive)
            .filter(brand -> popular == null || brand.isPopular() == popular)
            .map(BrandDto::of)
            .toList();
   }

   @GetMapping("/brands/{id}/models")
   @Operation(summary = "Модели марки с поколениями", description = "Шторка моделей (28), выбор машины (04, 26)")
   public List<ModelDto> models(@PathVariable Long id, @RequestParam(required = false) String q) {
      return directory.searchModels(q, id, Integer.MAX_VALUE).stream().filter(CarModel::isActive).map(ModelDto::of)
            .toList();
   }

   @GetMapping("/models")
   @Operation(summary = "Поиск модели по всем маркам", description = "«камри 50», «мерс w211», «e класс»")
   public List<ModelSearchItem> searchModels(@RequestParam String q) {
      return directory.searchModels(q, null, MODEL_SEARCH_LIMIT * 2).stream()
            .filter(model -> model.isActive() && directory.brand(model.getBrandId()).isActive())
            .limit(MODEL_SEARCH_LIMIT)
            .map(model -> new ModelSearchItem(ModelDto.of(model), BrandDto.of(directory.brand(model.getBrandId()))))
            .toList();
   }
}
