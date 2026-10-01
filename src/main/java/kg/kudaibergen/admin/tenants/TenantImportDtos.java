package kg.kudaibergen.admin.tenants;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/** Импорт арендаторов: предпросмотр и отчёт. */
public final class TenantImportDtos {

   private TenantImportDtos() {
   }

   /** Ошибка строки файла: line — номер строки как в Excel. */
   public record TenantImportError(int line, String message) {
   }

   /**
    * Строка, которая что-то поменяет: label — «Ряд 14 · 12». Пустая ячейка в файле значение не стирает,
    * поэтому new* = null означает «останется как было».
    */
   public record TenantChange(int line, Long containerId, String label, Side side, @Nullable String oldName,
                              @Nullable String newName, @Nullable @MaskedPhone String oldPhone,
                              @Nullable @MaskedPhone String newPhone) {
   }

   /**
    * status: PREVIEW — ждёт применения, APPLIED — применён. changed — сколько контейнеров изменится;
    * changes — первые 200 из них; errors — все ошибки (строки с ошибками не применяются).
    */
   public record TenantImportDto(Long id, String fileName, String status, int rowsTotal, int rowsOk, int rowsError,
                                 int changed, List<TenantChange> changes, List<TenantImportError> errors,
                                 Instant createdAt, @Nullable Instant appliedAt) {
   }
}
