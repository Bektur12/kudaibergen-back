package kg.kudaibergen.garage.dto;

import kg.kudaibergen.garage.entity.CarModel;
import org.springframework.lang.Nullable;

/** label — готовая подпись «Camry 50», «E-класс W211». */
public record ModelDto(Long id, Long brandId, String name, @Nullable String generation, String label, @Nullable Short yearFrom,
                       @Nullable Short yearTo) {

   public static ModelDto of(CarModel model) {
      return new ModelDto(model.getId(), model.getBrandId(), model.getName(), model.getGeneration(), model.label(),
            model.getYearFrom(), model.getYearTo());
   }
}
