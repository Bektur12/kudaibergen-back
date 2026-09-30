package kg.kudaibergen.chat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.master.MasterRepository;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.stereotype.Component;

/**
 * Исполнитель в чате — магазин или мастер. Здесь всё, что от него зависит: кто пишет за него, действует ли
 * он, как называется. Остальной чат не различает магазин и мастера.
 */
@Component
public class ChatProviders {

   private final ShopRepository shops;
   private final ShopMemberRepository members;
   private final MasterRepository masters;

   public ChatProviders(ShopRepository shops, ShopMemberRepository members, MasterRepository masters) {
      this.shops = shops;
      this.members = members;
      this.masters = masters;
   }

   /** Люди исполнителя: все люди бокса или сам мастер. */
   public List<Long> userIds(Chat chat) {
      if (chat.withMaster()) {
         return masters.findById(chat.getMasterId()).map(master -> List.of(master.getOwnerId())).orElse(List.of());
      }
      return members.findByShopIdOrderByCreatedAtAsc(chat.getShopId()).stream().map(ShopMember::getUserId).toList();
   }

   public boolean isProviderUser(Long userId, Chat chat) {
      if (chat.withMaster()) {
         return masters.findByOwnerId(userId).map(Master::getId).filter(id -> id.equals(chat.getMasterId())).isPresent();
      }
      return members.findByUserId(userId).map(ShopMember::getShopId)
            .filter(shopId -> Objects.equals(shopId, chat.getShopId())).isPresent();
   }

   /** Писать можно, пока исполнитель действует. */
   public boolean active(Chat chat) {
      return chat.withMaster() ? master(chat).map(Master::isActive).orElse(false)
            : shop(chat).map(Shop::isActive).orElse(false);
   }

   public String name(Chat chat) {
      return chat.withMaster() ? master(chat).map(Master::getName).orElse("")
            : shop(chat).map(Shop::getName).orElse("");
   }

   public Optional<Shop> shop(Chat chat) {
      return chat.getShopId() == null ? Optional.empty() : shops.findById(chat.getShopId());
   }

   public Optional<Master> master(Chat chat) {
      return chat.getMasterId() == null ? Optional.empty() : masters.findById(chat.getMasterId());
   }

   /** Профиль мастера пользователя; null — не мастер. */
   public Long masterIdOf(Long userId) {
      return masters.findByOwnerId(userId).map(Master::getId).orElse(null);
   }
}
