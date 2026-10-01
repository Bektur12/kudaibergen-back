package kg.kudaibergen.market.admin;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.auth.dto.PhoneFormat;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/** Запросы и ответы админки карты. */
public final class AdminMarketDtos {

   private AdminMarketDtos() {
   }

   /** Сколько мест на каждой стороне ряда: {"counts":{"NORTH":15,"SOUTH":14}}. */
   public record ContainerCountsRequest(
         @NotEmpty Map<Side, @NotNull @Min(0) @Max(200) Integer> counts) {
   }

   /**
    * Правка контейнера; null — не менять. tenantPhone / tenantName — арендатор из базы рынка (пустая строка — убрать).
    * side / number / posInRow — перенумерация (сторона — из сторон ряда), active = false — выключить место.
    */
   public record UpdateContainerRequest(
         @Pattern(regexp = "|" + PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String tenantPhone,
         Boolean active,
         @Size(max = 120) String tenantName,
         Side side,
         @Min(1) @Max(500) Integer number,
         @Min(1) @Max(500) Integer posInRow) {

      public UpdateContainerRequest(String tenantPhone, Boolean active) {
         this(tenantPhone, active, null, null, null, null);
      }
   }

   /** Новое место в ряду; posInRow по умолчанию = number. */
   public record CreateContainerRequest(
         @NotNull Long rowId,
         @NotNull Side side,
         @NotNull @Min(1) @Max(500) Integer number,
         @Min(1) @Max(500) Integer posInRow,
         @Size(max = 120) String tenantName,
         @Pattern(regexp = "|" + PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String tenantPhone) {
   }

   public record AdminContainerDto(Long id, Long rowId, String rowCode, Side side, int number, boolean active,
                                   @Nullable String tenantPhone, String qrToken, int posInRow,
                                   @Nullable String tenantName) {
   }

   public record AnchorDto(
         @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
         @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
         @NotNull Double x,
         @NotNull Double y,
         @Size(max = 60) String label) {
   }

   public record GeoAnchorsRequest(@NotNull @Size(min = 3, max = 20) List<@Valid AnchorDto> anchors) {
   }

   /** Результат калибровки: масштаб и невязка каждой точки (если > 10 м — точку сняли неточно). */
   public record CalibrationDto(double metersPerPx, double maxResidualM, List<AnchorResidual> anchors) {

      public record AnchorResidual(String label, double residualM) {
      }
   }

   /** Новая версия схемы — в формате market-map.json плюс ряды с кодами. */
   public record MapUploadRequest(
         @NotNull JsonNode boundary,
         @NotNull JsonNode blocks,
         @NotNull JsonNode passages,
         @NotNull JsonNode entrances,
         JsonNode pois,
         JsonNode streets,
         JsonNode labels,
         @Positive Double metersPerPx,
         @NotEmpty List<@Valid RowUpload> rows) {
   }

   public record RowUpload(
         @NotBlank @Size(max = 20) String code,
         @NotBlank @Size(max = 40) String label,
         @NotNull RowType type,
         @NotNull JsonNode geometry,
         @NotNull @Min(0) Integer sortOrder) {
   }

   public record PublishedMapDto(int version, int rows, int deactivatedRows) {
   }
}
