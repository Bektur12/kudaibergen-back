package kg.kudaibergen.admin.masters;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.admin.sanctions.SanctionDto;
import kg.kudaibergen.auth.dto.PhoneFormat;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.MasterInputs;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.lang.Nullable;

/** DTO раздела «Мастера» [A7]. */
public final class AdminMasterDtos {

   private AdminMasterDtos() {
   }

   /**
    * Строка списка. services — коды услуг (названия — из GET /service-types). Место: mobile = false —
    * «{address} · {radiusKm} км», true — «выезд · {radiusKm} км». phone — телефон для клиентов.
    */
   public record AdminMasterRow(Long id, String name, @Nullable String avatarUrl, ShopStatus status, boolean warned,
                                @MaskedPhone String ownerPhone, @Nullable @MaskedPhone String phone,
                                List<String> services, String address, boolean mobile, int radiusKm,
                                MasterCheck check, int photos, long openComplaints, BigDecimal rating,
                                int reviewsCount, Instant createdAt) {
   }

   public record MasterTabCounts(long all, long pending, long mobile, long blocked, long rejected) {
   }

   public record AdminMasterOwnerDto(Long userId, @Nullable String name, @MaskedPhone String phone) {
   }

   /** За 30 дней: заявок пришло, откликов «Могу помочь», «Договорились». */
   public record AdminMasterStatsDto(long received, long offers, long deals) {
   }

   /** Карточка мастера. statusReason — причина блокировки или отказа. brands пусто при allBrands. */
   public record AdminMasterDetailDto(Long id, String publicId, String name, @Nullable String avatarUrl,
                                      ShopStatus status, @Nullable String statusReason, boolean warned,
                                      AdminMasterOwnerDto owner, @Nullable @MaskedPhone String phone,
                                      String address, double lat, double lng, int radiusKm, boolean mobile,
                                      List<String> services, boolean allBrands, List<String> brands,
                                      Set<CarOrigin> origins, LocalTime openFrom, LocalTime openTo,
                                      Set<DayOfWeek> workDays, boolean accepting, BigDecimal rating,
                                      int reviewsCount, List<PhotoDto> photos, MasterCheck check,
                                      AdminMasterStatsDto stats30d, long openComplaints, long complaints90d,
                                      long warnings90d, List<SanctionDto> sanctions, Instant createdAt) {
   }

   public record MasterReasonRequest(@NotBlank(message = "Укажите причину") @Size(max = 300) String reason) {
   }

   /**
    * «+ Добавить вручную»: профиль за мастера. Пользователь по phone создаётся, если его нет; его режим
    * приложения не меняется. ownerName — имя пользователя, если у него ещё нет имени. Профиль сразу действует.
    */
   public record CreateMasterRequest(
         @NotBlank(message = "Укажите телефон мастера")
         @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String ownerPhone,
         @Size(max = 120) String ownerName,
         @NotNull @Valid MasterInputs.CreateMaster profile) {
   }
}
