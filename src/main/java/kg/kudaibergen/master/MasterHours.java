package kg.kudaibergen.master;

import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.WeekDays;
import org.springframework.stereotype.Component;

/**
 * Принимает ли мастер (39): часов работы и переключателя «Принимаю» больше нет — заявки приходят каждому
 * действующему мастеру в любое время.
 */
@Component
public class MasterHours {

   public boolean openNow(Master master) {
      return master.isActive();
   }

   /** Получает ли заявки: мастер действует. */
   public boolean receivesRequests(Master master) {
      return master.isActive();
   }

   public OpenStateDto state(Master master) {
      return new OpenStateDto(openNow(master), false, master.getOpenFrom(), master.getOpenTo(),
            WeekDays.fromMask(master.getWorkDays()), null);
   }
}
