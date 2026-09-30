package kg.kudaibergen.admin.search;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kg.kudaibergen.admin.access.MaskedPhone;
import org.springframework.lang.Nullable;

/**
 * Поиск из шапки админки, результаты группами (до 5 в группе). Группа null — у сотрудника нет права
 * на этот раздел (USERS_VIEW, SELLERS_VIEW, MASTERS_VIEW, MARKET_VIEW); пустой список — ничего не нашлось.
 */
public record AdminSearchDto(@Nullable List<SearchUserHit> users, @Nullable List<SearchShopHit> shops,
                             @Nullable List<SearchMasterHit> masters, @Nullable List<SearchContainerHit> containers) {

   public record SearchUserHit(Long id, @MaskedPhone String phone, @Nullable String name, boolean blocked,
                               Instant createdAt) {
   }

   /** location — «Ряд 14 · 12»; null, если магазин без места. */
   public record SearchShopHit(Long id, String name,
                               @Schema(allowableValues = {"PENDING_VERIFICATION", "ACTIVE", "BLOCKED"}) String status,
                               @Nullable String location, @MaskedPhone String ownerPhone) {
   }

   public record SearchMasterHit(Long id, String name,
                                 @Schema(allowableValues = {"PENDING_VERIFICATION", "ACTIVE", "BLOCKED"}) String status,
                                 String address, @MaskedPhone String ownerPhone) {
   }

   /** label — «Ряд 14 · 12»; shop — кто стоит в контейнере; tenantPhone — арендатор по базе рынка. */
   public record SearchContainerHit(Long id, Long rowId, String label,
                                    @Schema(allowableValues = {"NORTH", "SOUTH", "WEST", "EAST"}) String side,
                                    @Nullable Long shopId, @Nullable String shopName,
                                    @Nullable @MaskedPhone String tenantPhone) {
   }
}
