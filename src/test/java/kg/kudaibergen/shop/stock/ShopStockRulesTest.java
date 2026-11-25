package kg.kudaibergen.shop.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.request.PartHintRepository;
import kg.kudaibergen.request.entity.PartHint;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockItemInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Проверка списка «всегда есть»: деталь из справочника, марка магазина, модель этой марки, без повторов. */
class ShopStockRulesTest {

   private static final long TOYOTA = 1L;
   private static final long LEXUS = 2L;
   private static final long CAMRY = 10L;
   private static final long RX = 20L;
   private static final long PADS = 100L;
   private static final long RADIATOR = 101L;
   private static final long HIDDEN = 102L;

   private final PartHintRepository hints = mock(PartHintRepository.class);
   private final VehicleDirectory directory = mock(VehicleDirectory.class);
   private final ShopStockService service = new ShopStockService(null, hints, directory, null, null, null);
   private final Set<Long> shopBrands = Set.of(TOYOTA);

   @BeforeEach
   void dictionary() {
      List<PartHint> all = List.of(hint(PADS, true), hint(RADIATOR, true), hint(HIDDEN, false));
      when(hints.findAllById(any())).thenAnswer(call -> {
         Iterable<Long> ids = call.getArgument(0);
         List<Long> wanted = new ArrayList<>();
         ids.forEach(wanted::add);
         return all.stream().filter(h -> wanted.contains(h.getId())).toList();
      });
      CarModel camry = model(CAMRY, TOYOTA);
      CarModel rx = model(RX, LEXUS);
      when(directory.model(CAMRY)).thenReturn(camry);
      when(directory.model(RX)).thenReturn(rx);
      when(directory.model(999L)).thenThrow(new NotFoundException("MODEL_NOT_FOUND", "Модель не найдена"));
   }

   @Test
   void пустойСписок_илиНеПередан_ничегоНеПишем() {
      assertThat(service.normalize(null, shopBrands)).isEmpty();
      assertThat(service.normalize(List.of(), shopBrands)).isEmpty();
   }

   @Test
   void деталь_наВсеМарки_наМарку_наМодель() {
      List<StockKey> keys = service.normalize(List.of(
            new StockItemInput(PADS, null, null),
            new StockItemInput(RADIATOR, TOYOTA, null),
            new StockItemInput(PADS, TOYOTA, CAMRY)), shopBrands);
      assertThat(keys).containsExactly(
            new StockKey(PADS, null, null),
            new StockKey(RADIATOR, TOYOTA, null),
            new StockKey(PADS, TOYOTA, CAMRY));
   }

   @Test
   void повторыУбираются_порядокОстаётся() {
      List<StockKey> keys = service.normalize(List.of(
            new StockItemInput(RADIATOR, null, null),
            new StockItemInput(PADS, TOYOTA, null),
            new StockItemInput(RADIATOR, null, null)), shopBrands);
      assertThat(keys).containsExactly(new StockKey(RADIATOR, null, null), new StockKey(PADS, TOYOTA, null));
   }

   @Test
   void незнакомаяИлиСкрытаяДеталь_400() {
      assertThatThrownBy(() -> service.normalize(List.of(new StockItemInput(555L, null, null)), shopBrands))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("555");
      assertThatThrownBy(() -> service.normalize(List.of(new StockItemInput(HIDDEN, null, null)), shopBrands))
            .isInstanceOf(BadRequestException.class);
   }

   @Test
   void маркаНеИзМарокМагазина_400() {
      assertThatThrownBy(() -> service.normalize(List.of(new StockItemInput(PADS, LEXUS, null)), shopBrands))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("Марки, которые продаю");
   }

   @Test
   void модельБезМарки_чужаяМодель_незнакомаяМодель_400() {
      assertThatThrownBy(() -> service.normalize(List.of(new StockItemInput(PADS, null, CAMRY)), shopBrands))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("вместе с маркой");
      assertThatThrownBy(() -> service.normalize(List.of(new StockItemInput(PADS, TOYOTA, RX)), shopBrands))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("модели");
      assertThatThrownBy(() -> service.normalize(List.of(new StockItemInput(PADS, TOYOTA, 999L)), shopBrands))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("модели");
   }

   @Test
   void большеЛимита_400() {
      List<StockItemInput> many = new ArrayList<>();
      for (int i = 0; i < 151; i++) {
         many.add(new StockItemInput(PADS, null, null));
      }
      assertThatThrownBy(() -> service.normalize(many, shopBrands)).isInstanceOf(BadRequestException.class);
   }

   private static PartHint hint(long id, boolean active) {
      PartHint hint = mock(PartHint.class);
      when(hint.getId()).thenReturn(id);
      when(hint.isActive()).thenReturn(active);
      return hint;
   }

   private static CarModel model(long id, long brandId) {
      CarModel model = mock(CarModel.class);
      when(model.getId()).thenReturn(id);
      when(model.getBrandId()).thenReturn(brandId);
      return model;
   }
}
