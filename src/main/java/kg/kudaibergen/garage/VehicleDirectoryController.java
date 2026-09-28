package kg.kudaibergen.garage;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.dto.ModelDto;
import kg.kudaibergen.garage.dto.ModelSearchItem;
import kg.kudaibergen.garage.entity.Brand;
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

   public VehicleDirectoryController(VehicleDirectory directory) {
      this.directory = directory;
   }

   @GetMapping("/brands")
   @Operation(summary = "Марки", description = """
         Сначала 8 популярных (popular=true, порядок плиток), затем остальные по алфавиту.
         q — поиск по марке или модели: «мерс», «бэха», «камри» (экраны 23, 28).""")
   public List<BrandDto> brands(@RequestParam(required = false) String q,
                                @RequestParam(required = false) Boolean popular) {
      List<Brand> found = q == null || q.isBlank() ? directory.brands() : directory.searchBrands(q);
      return found.stream()
            .filter(brand -> popular == null || brand.isPopular() == popular)
            .map(BrandDto::of)
            .toList();
   }

   @GetMapping("/brands/{id}/models")
   @Operation(summary = "Модели марки с поколениями", description = "Шторка моделей (28), выбор машины (04, 26)")
   public List<ModelDto> models(@PathVariable Long id, @RequestParam(required = false) String q) {
      return directory.searchModels(q, id, Integer.MAX_VALUE).stream().map(ModelDto::of).toList();
   }

   @GetMapping("/models")
   @Operation(summary = "Поиск модели по всем маркам", description = "«камри 50», «мерс w211», «e класс»")
   public List<ModelSearchItem> searchModels(@RequestParam String q) {
      return directory.searchModels(q, null, MODEL_SEARCH_LIMIT).stream()
            .map(model -> new ModelSearchItem(ModelDto.of(model), BrandDto.of(directory.brand(model.getBrandId()))))
            .toList();
   }
}
