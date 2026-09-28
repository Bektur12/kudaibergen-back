package kg.kudaibergen.shop;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.shop.entity.MemberRole;
import kg.kudaibergen.shop.entity.ShopMember;
import kg.kudaibergen.shop.entity.ShopMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopMemberRepository extends JpaRepository<ShopMember, ShopMemberId> {

   Optional<ShopMember> findByUserId(Long userId);

   List<ShopMember> findByShopIdOrderByCreatedAtAsc(Long shopId);

   long countByShopIdAndRole(Long shopId, MemberRole role);
}
