package kg.kudaibergen.category;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.i18n.Langs;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Справочник категорий")
public class CategoryController {

   private final CategoryService categories;

   public CategoryController(CategoryService categories) {
      this.categories = categories;
   }

   @GetMapping
   @Operation(summary = "Категории запчастей", description = "Чипы на экранах 10, 15, 26, 27. Язык — Accept-Language")
   public List<CategoryDto> list(@Parameter(hidden = true)
                                 @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      var lang = Langs.fromHeader(language);
      return categories.all().stream().filter(category -> category.isActive()).map(category -> CategoryDto.of(category, lang)).toList();
   }
}
