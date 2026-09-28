package kg.kudaibergen.chat.dto;

import kg.kudaibergen.chat.entity.ReplyTemplate;

public record ReplyTemplateDto(Long id, String text, int sortOrder) {

   public static ReplyTemplateDto of(ReplyTemplate template) {
      return new ReplyTemplateDto(template.getId(), template.getText(), template.getSortOrder());
   }
}
