package kg.kudaibergen.master;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Кому уходит заявка (12.4): мастер действует, «Принимаю» включено, сейчас его рабочее время, услуга в его
 * списке, марка — в его марках (или «Все марки»), страна машины — в его списке (или список пуст, или «не знаю»),
 * расстояние от мастера до клиента не больше меньшего из радиусов заявки и мастера. Свой профиль клиенту не шлём.
 */
@Component
public class ServiceRecipientFinder {

   private final MasterRepository masters;
   private final MasterHours hours;

   public ServiceRecipientFinder(MasterRepository masters, MasterHours hours) {
      this.masters = masters;
      this.hours = hours;
   }

   /** Мастер и расстояние до клиента в метрах; ближние — первыми. */
   public record Match(Master master, int distanceM) {
   }

   @Transactional(readOnly = true)
   public List<Match> find(Long buyerId, String service, Long brandId, CarOrigin origin, double lat, double lng,
                           int radiusKm) {
      return masters.findReceiving(ShopStatus.ACTIVE, service).stream()
            .filter(master -> !Objects.equals(master.getOwnerId(), buyerId))
            .filter(master -> master.worksWith(brandId, origin))
            .filter(hours::openNow)
            .map(master -> new Match(master, Distances.meters(lat, lng, master.getLat(), master.getLng())))
            .filter(match -> match.distanceM() <= Math.min(radiusKm, match.master().getRadiusKm()) * 1000)
            .sorted(Comparator.comparingInt(Match::distanceM))
            .toList();
   }
}
