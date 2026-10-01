package kg.kudaibergen.chat;

import java.util.List;

import kg.kudaibergen.chat.dto.ChatInputs;
import kg.kudaibergen.chat.dto.ReplyTemplateDto;
import kg.kudaibergen.chat.entity.ReplyTemplate;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.shop.ShopAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Свои шаблоны ответов бокса: общие для владельца и сотрудников (оба отвечают в чатах, ТЗ 2).
 * В чате показываются после быстрых ответов «Отложил для вас», «Как пройти», «Продано».
 */
@Service
public class ReplyTemplateService {

   static final int MAX_TEMPLATES = 30;

   private final ReplyTemplateRepository templates;
   private final ShopAccess access;

   public ReplyTemplateService(ReplyTemplateRepository templates, ShopAccess access) {
      this.templates = templates;
      this.access = access;
   }

   @Transactional(readOnly = true)
   public List<ReplyTemplateDto> list(Long userId) {
      return templates.findByShopIdOrderBySortOrderAscIdAsc(shopId(userId)).stream()
            .map(ReplyTemplateDto::of).toList();
   }

   @Transactional
   public ReplyTemplateDto create(Long userId, ChatInputs.Template input) {
      Long shopId = shopId(userId);
      if (templates.countByShopId(shopId) >= MAX_TEMPLATES) {
         throw new ConflictException("TEMPLATES_LIMIT", "Не больше " + MAX_TEMPLATES + " шаблонов");
      }
      short sort = (short) (input.sortOrder() == null ? 100 : input.sortOrder());
      return ReplyTemplateDto.of(templates.save(new ReplyTemplate(shopId, input.text().trim(), sort)));
   }

   @Transactional
   public ReplyTemplateDto update(Long userId, Long id, ChatInputs.Template input) {
      ReplyTemplate template = owned(userId, id);
      template.setText(input.text().trim());
      if (input.sortOrder() != null) {
         template.setSortOrder(input.sortOrder().shortValue());
      }
      return ReplyTemplateDto.of(template);
   }

   @Transactional
   public void delete(Long userId, Long id) {
      templates.delete(owned(userId, id));
   }

   private ReplyTemplate owned(Long userId, Long id) {
      return templates.findByIdAndShopId(id, shopId(userId))
            .orElseThrow(() -> new NotFoundException("TEMPLATE_NOT_FOUND", "Шаблон не найден"));
   }

   private Long shopId(Long userId) {
      return access.requireMember(userId).shop().getId();
   }
}
