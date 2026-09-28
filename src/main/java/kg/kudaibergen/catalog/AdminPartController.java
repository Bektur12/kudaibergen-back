package kg.kudaibergen.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Модерация каталога по жалобам (ТЗ 13.1) — админ рынка и суперадмин. */
@RestController
@RequestMapping("/api/v1/admin/parts")
@Tag(name = "Админка: запчасти")
public class AdminPartController {

   private final MyPartsService parts;

   public AdminPartController(MyPartsService parts) {
      this.parts = parts;
   }

   @DeleteMapping("/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить запчасть (модерация)")
   public void delete(@PathVariable Long id) {
      parts.adminDelete(id);
   }
}
