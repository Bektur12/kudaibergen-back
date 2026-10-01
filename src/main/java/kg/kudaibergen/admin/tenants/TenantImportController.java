package kg.kudaibergen.admin.tenants;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.tenants.TenantImportDtos.TenantImportDto;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** «Импорт арендаторов» [A2]: загрузка → предпросмотр с отчётом → применение. */
@RestController
@RequestMapping("/api/v1/admin/tenants/import")
@Tag(name = "Админка: продавцы")
public class TenantImportController {

   static final long MAX_FILE = 5L * 1024 * 1024;

   private final TenantImportService imports;

   public TenantImportController(TenantImportService imports) {
      this.imports = imports;
   }

   @GetMapping(value = "/template.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   @PreAuthorize("hasAuthority('TENANTS_IMPORT')")
   @Operation(summary = "Шаблон списка арендаторов (xlsx)")
   public ResponseEntity<byte[]> template() {
      return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                  ContentDisposition.attachment().filename("tenants.xlsx").build().toString())
            .body(TenantSheet.template());
   }

   @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @PreAuthorize("hasAuthority('TENANTS_IMPORT')")
   @Audited(action = "TENANTS_IMPORT_PREVIEW", entity = "TENANT_IMPORT")
   @Operation(summary = "Загрузить список (предпросмотр)", description = "xlsx или csv до 5 МБ: ряд, номер, сторона, "
         + "ФИО, телефон. Ничего не меняет — показывает, что изменится, и ошибки по строкам")
   @ApiResponse(responseCode = "400", description = "BAD_FILE, EMPTY_FILE, FILE_TOO_LARGE")
   public TenantImportDto upload(@RequestParam("file") MultipartFile file,
                                 @AuthenticationPrincipal AuthPrincipal admin) throws IOException {
      if (file.isEmpty() || file.getSize() > MAX_FILE) {
         throw new BadRequestException("FILE_TOO_LARGE", "Файл пустой или больше 5 МБ");
      }
      TenantImportDto preview = imports.preview(file.getInputStream(), file.getOriginalFilename(), admin.userId());
      AuditTrail.entityId(preview.id());
      AuditTrail.after(new Summary(preview.fileName(), preview.rowsTotal(), preview.rowsOk(), preview.rowsError(),
            preview.changed()));
      return preview;
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('TENANTS_IMPORT')")
   @Operation(summary = "Предпросмотр или отчёт импорта")
   public TenantImportDto get(@PathVariable Long id) {
      return imports.get(id);
   }

   @PostMapping("/{id}/apply")
   @PreAuthorize("hasAuthority('TENANTS_IMPORT')")
   @Audited(action = "TENANTS_IMPORT_APPLY", entity = "TENANT_IMPORT", id = "#id")
   @Operation(summary = "Применить", description = "Записывает ФИО и телефоны арендаторов в контейнеры. Строки с "
         + "ошибками пропускаются. Предпросмотр действует 24 часа")
   @ApiResponse(responseCode = "409", description = "IMPORT_APPLIED, IMPORT_EXPIRED")
   public TenantImportDto apply(@PathVariable Long id) {
      TenantImportDto applied = imports.apply(id);
      AuditTrail.after(new Summary(applied.fileName(), applied.rowsTotal(), applied.rowsOk(), applied.rowsError(),
            applied.changed()));
      return applied;
   }

   /** В журнал — итог, а не весь список (в нём телефоны и сотни строк). */
   record Summary(String fileName, int rowsTotal, int rowsOk, int rowsError, int changed) {
   }
}
