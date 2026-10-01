package kg.kudaibergen.master;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.master.dto.ServiceTypeDto;
import kg.kudaibergen.master.entity.ServiceDuration;
import kg.kudaibergen.master.entity.ServiceType;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Справочник услуг (33, 38): список плиток и проверка кодов. Меняется миграциями, читается каждый раз. */
@Component
public class ServiceCatalog {

   private final ServiceTypeRepository types;

   public ServiceCatalog(ServiceTypeRepository types) {
      this.types = types;
   }

   @Transactional(readOnly = true)
   public Map<String, ServiceType> byCode() {
      Map<String, ServiceType> result = new LinkedHashMap<>();
      types.findAllByOrderBySortOrderAsc().forEach(type -> result.put(type.getCode(), type));
      return result;
   }

   public ServiceType require(String code) {
      ServiceType type = byCode().get(code);
      if (type == null) {
         throw new BadRequestException("BAD_SERVICE", "Нет такой услуги");
      }
      return type;
   }

   /** Услуга для новой заявки: скрытую выбрать нельзя. */
   public ServiceType requireActive(String code) {
      ServiceType type = require(code);
      if (!type.isActive()) {
         throw new BadRequestException("SERVICE_HIDDEN", "Эта услуга сейчас недоступна");
      }
      return type;
   }

   /** Услуги мастера: только действующие (скрытая услуга, уже выбранная раньше, остаётся). */
   public Set<String> requireAll(Collection<String> codes) {
      Map<String, ServiceType> known = byCode();
      if (codes == null || codes.isEmpty() || !known.keySet().containsAll(codes)) {
         throw new BadRequestException("BAD_SERVICE", "Выберите услуги из списка");
      }
      return Set.copyOf(codes);
   }

   /** Плитки услуг: скрытые не показываются. */
   public List<ServiceTypeDto> list(Lang lang) {
      return byCode().values().stream().filter(ServiceType::isActive).map(type -> dto(type, lang)).toList();
   }

   public List<ServiceTypeDto> dtos(Collection<String> codes, Lang lang) {
      Map<String, ServiceType> known = byCode();
      return known.values().stream().filter(type -> codes.contains(type.getCode())).map(type -> dto(type, lang))
            .collect(Collectors.toList());
   }

   public static ServiceTypeDto dto(ServiceType type, Lang lang) {
      return new ServiceTypeDto(type.getCode(), type.name(lang), type.getIcon(), type.isNeedsLocation(), type.isUrgent(),
            ServiceDuration.ofMinutes(type.getDurationMin()));
   }
}
