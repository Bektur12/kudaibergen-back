package kg.kudaibergen.chat;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.chat.dto.ChatInputs;
import kg.kudaibergen.chat.dto.ReplyTemplateDto;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Свои шаблоны ответов бокса — ведут владелец и сотрудники. */
@RestController
@RequestMapping("/api/v1/my/shop/reply-templates")
@Tag(name = "Чаты")
public class ReplyTemplateController {

   private final ReplyTemplateService templates;

   public ReplyTemplateController(ReplyTemplateService templates) {
      this.templates = templates;
   }

   @GetMapping
   @Operation(summary = "Шаблоны ответов бокса")
   public List<ReplyTemplateDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
      return templates.list(principal.userId());
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Добавить шаблон", description = "До 300 символов, не больше 30 шаблонов на бокс")
   public ReplyTemplateDto create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody ChatInputs.Template request) {
      return templates.create(principal.userId(), request);
   }

   @PutMapping("/{id}")
   @Operation(summary = "Изменить шаблон")
   public ReplyTemplateDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody ChatInputs.Template request) {
      return templates.update(principal.userId(), id, request);
   }

   @DeleteMapping("/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить шаблон")
   public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      templates.delete(principal.userId(), id);
   }
}
