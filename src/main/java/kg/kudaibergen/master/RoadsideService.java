package kg.kudaibergen.master;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kg.kudaibergen.common.ratelimit.RateLimiter;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.MasterCardDto;
import kg.kudaibergen.master.dto.NearbyMasterDto;
import kg.kudaibergen.master.dto.OfflinePackDto;
import kg.kudaibergen.master.dto.OfflinePackDto.EmergencyNumber;
import kg.kudaibergen.master.dto.OfflinePackDto.PackMaster;
import kg.kudaibergen.master.dto.OfflinePackDto.PackServiceType;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.master.entity.ServiceType;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Помощь на дороге: «Кто рядом» сразу, без заявки и ожидания откликов, и справочник мастеров на телефон —
 * на перевале (Бишкек–Ош) интернета нет, а GPS, звонок и SMS работают.
 * Ближайших показываем без обрезки по радиусу: на трассе ближайший эвакуатор может быть за 100 км.
 */
@Service
public class RoadsideService {

   static final int DEFAULT_LIMIT = 20;
   static final int MAX_LIMIT = 50;
   /** Полных скачиваний справочника в сутки на человека: номера мастеров не должны утекать пачками. */
   static final int PACK_DAILY_LIMIT = 20;

   static final List<EmergencyNumber> EMERGENCY = List.of(
         new EmergencyNumber("112", "Единая служба спасения", "Бирдиктүү куткаруу кызматы"),
         new EmergencyNumber("103", "Скорая помощь", "Тез жардам"),
         new EmergencyNumber("102", "Милиция", "Милиция"),
         new EmergencyNumber("101", "Пожарная служба", "Өрт өчүрүү кызматы"));

   /** Действующие мастера одним запросом: услуги (только не скрытые), марки и страны — массивами. */
   private static final String ACTIVE_MASTERS = """
         select m.id, m.owner_id, m.name, m.phone, m.lat, m.lng, m.address, m.is_mobile, m.radius_km, m.rating,
                m.reviews_count, m.all_brands,
                (select array_agg(s.service order by t.sort_order) from master_services s
                   join service_types t on t.code = s.service and t.is_active
                  where s.master_id = m.id) as services,
                (select array_agg(b.brand_id order by b.brand_id) from master_brands b
                  where b.master_id = m.id) as brands,
                (select array_agg(o.origin order by o.origin) from master_origins o
                  where o.master_id = m.id) as origins
           from masters m
          where m.status = 'ACTIVE'
          order by m.id
         """;

   private final JdbcTemplate jdbc;
   private final MasterRepository masters;
   private final MasterMapper mapper;
   private final ServiceCatalog catalog;
   private final ServiceRequestService requests;
   private final RateLimiter rateLimiter;
   private final Clock clock;

   public RoadsideService(JdbcTemplate jdbc, MasterRepository masters, MasterMapper mapper, ServiceCatalog catalog,
                          ServiceRequestService requests, RateLimiter rateLimiter, Clock clock) {
      this.jdbc = jdbc;
      this.masters = masters;
      this.mapper = mapper;
      this.catalog = catalog;
      this.requests = requests;
      this.rateLimiter = rateLimiter;
      this.clock = clock;
   }

   /** Мастер из справочника и чей он (свой профиль себе не показываем). */
   record RoadsideRow(long ownerId, PackMaster master) {
   }

   /** Найденный рядом: расстояние, доедет ли, берётся ли за машину (null — машина не указана). */
   record RoadsideMatch(RoadsideRow row, int distanceM, boolean reachesYou, Boolean worksWithCar) {
   }

   // ─────────────────────── кто рядом ───────────────────────

   @Transactional(readOnly = true)
   public List<NearbyMasterDto> nearby(Long userId, double lat, double lng, String service, Long carId, Long brandId,
                                       CarOrigin origin, Integer limit) {
      if (service != null) {
         catalog.require(service);
      }
      ServiceRequest.CarSnapshot car = carId == null && brandId == null ? null
            : requests.carFor(userId, carId, brandId, origin);
      int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
      List<RoadsideMatch> ranked = rank(rows(), userId, lat, lng, service, car, size);
      Map<Long, MasterCardDto> cards = mapper.cards(masters.findAllById(
            ranked.stream().map(found -> found.row().master().id()).toList()));
      return ranked.stream()
            .filter(found -> cards.containsKey(found.row().master().id()))
            .map(found -> new NearbyMasterDto(cards.get(found.row().master().id()), found.distanceM(),
                  found.row().master().services(), found.reachesYou(), found.worksWithCar()))
            .toList();
   }

   /**
    * Порядок «Кто рядом»: сначала те, кто доедет (вы в их радиусе) и берётся за машину, потом остальные; внутри —
    * по расстоянию. Без обрезки по радиусу — ближайший за 120 км тоже в списке.
    */
   static List<RoadsideMatch> rank(List<RoadsideRow> rows, Long userId, double lat, double lng, String service,
                            ServiceRequest.CarSnapshot car, int limit) {
      return rows.stream()
            .filter(row -> !Objects.equals(row.ownerId(), userId))
            .filter(row -> service == null || row.master().services().contains(service))
            .filter(row -> !row.master().services().isEmpty())
            .map(row -> {
               PackMaster master = row.master();
               int distance = Distances.meters(lat, lng, master.lat(), master.lng());
               Boolean works = car == null ? null : Master.worksWith(master.allBrands(), master.brandIds(),
                     master.origins(), car.brandId(), car.origin());
               return new RoadsideMatch(row, distance, distance <= master.radiusKm() * 1000, works);
            })
            .sorted(Comparator.comparing((RoadsideMatch found) -> !(found.reachesYou() && !Boolean.FALSE.equals(found.worksWithCar())))
                  .thenComparingInt(RoadsideMatch::distanceM))
            .limit(limit)
            .toList();
   }

   // ─────────────────────── справочник на телефон ───────────────────────

   /** Весь справочник. Считается как скачивание (лимит в сутки); 304 по ETag — не считается. */
   @Transactional(readOnly = true)
   public OfflinePackDto pack() {
      List<PackServiceType> types = serviceTypes();
      List<PackMaster> list = rows().stream().map(RoadsideRow::master).filter(master -> !master.services().isEmpty())
            .toList();
      return new OfflinePackDto(version(types, list), clock.instant(), types, EMERGENCY, list);
   }

   /** who — «user:42» или «ip:…» у гостя. */
   public void countDownload(String who) {
      rateLimiter.hit("roadside-pack:" + who, PACK_DAILY_LIMIT, Duration.ofDays(1), "PACK_RATE_LIMITED",
            "Справочник уже скачан много раз за сутки — попробуйте завтра");
   }

   @Transactional(readOnly = true)
   public OfflinePackDto.Version version() {
      OfflinePackDto pack = pack();
      return new OfflinePackDto.Version(pack.version(), pack.masters().size());
   }

   private List<PackServiceType> serviceTypes() {
      return catalog.byCode().values().stream().filter(ServiceType::isActive)
            .map(type -> new PackServiceType(type.getCode(), type.name(Lang.RU),
                  type.name(Lang.KG), type.getIcon(), type.isUrgent(),
                  type.getMaxRadiusKm()))
            .toList();
   }

   /** Версия — отпечаток содержимого: меняется при любой правке мастера, услуг, блокировке, отзыве. */
   static String version(List<PackServiceType> types, List<PackMaster> masters) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         digest.update(types.toString().getBytes(StandardCharsets.UTF_8));
         digest.update(masters.toString().getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(digest.digest(), 0, 8);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   private List<RoadsideRow> rows() {
      return jdbc.query(ACTIVE_MASTERS, RoadsideService::row);
   }

   private static RoadsideRow row(ResultSet rs, int n) throws SQLException {
      PackMaster master = new PackMaster(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
            rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("address"),
            strings(rs.getArray("services")), rs.getBoolean("is_mobile"), rs.getInt("radius_km"),
            rs.getBigDecimal("rating"), rs.getInt("reviews_count"), rs.getBoolean("all_brands"),
            longs(rs.getArray("brands")),
            strings(rs.getArray("origins")).stream().map(CarOrigin::valueOf).toList());
      return new RoadsideRow(rs.getLong("owner_id"), master);
   }

   private static List<String> strings(Array array) throws SQLException {
      return array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
   }

   private static List<Long> longs(Array array) throws SQLException {
      return array == null ? List.of()
            : Arrays.stream((Object[]) array.getArray()).map(value -> ((Number) value).longValue()).toList();
   }
}
