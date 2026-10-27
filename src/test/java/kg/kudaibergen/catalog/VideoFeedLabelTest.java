package kg.kudaibergen.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import kg.kudaibergen.catalog.entity.Fitment;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import org.junit.jupiter.api.Test;

/** «Подходят на Camry 50 и 55 · 2011–2017» под видео. */
class VideoFeedLabelTest {

   private final VehicleDirectory directory = mock(VehicleDirectory.class);
   private final VideoFeedService feed = new VideoFeedService(null, null, null, null, null, null, null, directory,
         null, null, null);

   @Test
   void generationsOfOneModelJoined() {
      model(50L, "Camry", "50", 2011, 2017);
      model(55L, "Camry", "55", 2014, 2017);
      assertThat(feed.fitsLabel(List.of(new Fitment(1L, 50L, null, null), new Fitment(1L, 55L, null, null))))
            .isEqualTo("Подходят на Camry 50 и 55 · 2011–2017");
   }

   @Test
   void brandOnlyAndFittingYears() {
      Brand lexus = mock(Brand.class);
      when(lexus.getName()).thenReturn("Lexus");
      when(directory.brand(2L)).thenReturn(lexus);
      model(50L, "Camry", "50", 2011, 2017);
      assertThat(feed.fitsLabel(List.of(new Fitment(1L, 50L, 2012, 2015), new Fitment(2L, null, 2010, null))))
            .isEqualTo("Подходят на Camry 50 и Lexus · с 2010");
      assertThat(feed.fitsLabel(List.of())).isNull();
   }

   private void model(Long id, String name, String generation, int from, int to) {
      CarModel model = mock(CarModel.class);
      when(model.getName()).thenReturn(name);
      when(model.getGeneration()).thenReturn(generation);
      when(model.getYearFrom()).thenReturn((short) from);
      when(model.getYearTo()).thenReturn((short) to);
      when(model.label()).thenReturn(name + " " + generation);
      when(directory.model(id)).thenReturn(model);
   }
}
