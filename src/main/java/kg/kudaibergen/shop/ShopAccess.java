package kg.kudaibergen.shop;

import java.util.Optional;

import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Права в боксе (ТЗ, раздел 2). Сотрудник отвечает на запросы и в чатах, ведёт товары,
 * открывает/закрывает бокс; профиль, марки, часы, сотрудников и переезд меняет только владелец.
 */
@Component
public class ShopAccess {

   private final ShopMemberRepository members;
   private final ShopRepository shops;

   public ShopAccess(ShopMemberRepository members, ShopRepository shops) {
      this.members = members;
      this.shops = shops;
   }

   @Transactional(readOnly = true)
   public Optional<Membership> membership(Long userId) {
      return members.findByUserId(userId)
            .map(member -> new Membership(shops.findById(member.getShopId()).orElseThrow(), member));
   }

   /** Владелец или сотрудник; нет бокса — 404 NO_SHOP (клиент ведёт на регистрацию 10а). */
   @Transactional(readOnly = true)
   public Membership requireMember(Long userId) {
      return membership(userId)
            .orElseThrow(() -> new NotFoundException("NO_SHOP", "У вас ещё нет бокса на рынке"));
   }

   @Transactional(readOnly = true)
   public Membership requireOwner(Long userId) {
      Membership membership = requireMember(userId);
      if (!membership.member().isOwner()) {
         throw new ForbiddenException("OWNER_ONLY", "Это может сделать только владелец бокса");
      }
      return membership;
   }

   public record Membership(Shop shop, ShopMember member) {
   }
}
