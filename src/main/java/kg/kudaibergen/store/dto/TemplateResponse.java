package kg.kudaibergen.store.dto;

import kg.kudaibergen.store.entity.ReplyTemplate;

public record TemplateResponse(Long id, String title, String body, short sortOrder) {

   public static TemplateResponse of(ReplyTemplate template) {
      return new TemplateResponse(template.getId(), template.getTitle(), template.getBody(),
            template.getSortOrder());
   }
}
