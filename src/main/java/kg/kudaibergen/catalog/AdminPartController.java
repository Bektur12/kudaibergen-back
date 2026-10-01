package kg.kudaibergen.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.audit.Audited;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Модерация каталога по жалобам (ТЗ 13.1), право CONTENT_REMOVE. */
@RestController
@RequestMapping("/api/v1/admin/parts")
@Tag(name = "Админка: запчасти")
public class AdminPartController {

   private final MyPartsService parts;

   public AdminPartController(MyPartsService parts) {
      this.parts = parts;
   }

   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('CONTENT_REMOVE')")
   @Audited(action = "PART_REMOVE", entity = "PART", id = "#id")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить запчасть (модерация)")
   public void delete(@PathVariable Long id) {
      parts.adminDelete(id);
   }
}
