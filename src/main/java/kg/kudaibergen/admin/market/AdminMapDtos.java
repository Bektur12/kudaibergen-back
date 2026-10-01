package kg.kudaibergen.admin.market;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.market.admin.AdminMarketDtos.MapUploadRequest;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/** Рынок и карта [A3]. */
public final class AdminMapDtos {

   private AdminMapDtos() {
   }

   /** «версия 7 · опубликована 21.09»: rows — рядов в схеме, calibrated — карта привязана к GPS. */
   public record MapVersionInfo(int version, Instant publishedAt, @Nullable Long publishedBy,
                                @Nullable String publishedByName, int rows, double metersPerPx, boolean calibrated) {
   }

   /**
    * Черновик схемы: data — в формате загрузки. valid / error — пройдёт ли публикация. outdated — после него
    * уже опубликовали другую версию (basedOnVersion не текущая).
    */
   public record MapDraftDto(int basedOnVersion, boolean outdated, Instant updatedAt, @Nullable Long updatedBy,
                             @Nullable String updatedByName, boolean valid, @Nullable String error,
                             MapUploadRequest data) {
   }

   /** current — что видят приложения; published — она же в формате загрузки (начать черновик); draft — если есть. */
   /** Комментарий к публикации — в журнал («добавлен ряд 31»). */
   public record PublishMapRequest(@Nullable @Size(max = 1000) String comment) {
   }

   public record AdminMapDto(MapVersionInfo current, MapUploadRequest published, @Nullable MapDraftDto draft) {
   }

   /**
    * Ряд со счётчиками «Контейнеров 16 · С продавцом 13 · Свободно 3 · Северная 8 · Южная 8».
    * containers / withShop / free / bySide — по включённым местам; disabled — выключенные.
    */
   public record AdminRowDto(Long id, String code, String label, RowType type, boolean active, int sortOrder,
                             long containers, long withShop, long free, long disabled, Map<Side, Long> bySide) {
   }

   public record RowCounts(long rows, long containers, long withShop, long free) {
   }

   public record GridShopDto(Long id, String name,
                             @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"PENDING_VERIFICATION",
                                   "ACTIVE", "BLOCKED"}) String status) {
   }

   /** Клетка сетки: shop — кто стоит, incoming — кто переезжает сюда (ждёт проверки). */
   public record GridCellDto(Long id, Side side, int number, int posInRow, boolean active,
                             @Nullable String tenantName, @Nullable @MaskedPhone String tenantPhone,
                             @Nullable GridShopDto shop, @Nullable GridShopDto incoming, String qrToken) {
   }

   public record GridSideDto(Side side, List<GridCellDto> containers) {
   }

   public record AdminRowDetailDto(AdminRowDto row, List<GridSideDto> sides) {
   }

   public record GeoAnchorItem(Long id, double lat, double lon, double x, double y, @Nullable String label) {
   }

   public record GeoAnchorsDto(List<GeoAnchorItem> anchors, boolean calibrated, double metersPerPx) {
   }
}
