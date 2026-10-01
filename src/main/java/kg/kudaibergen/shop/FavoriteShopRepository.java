package kg.kudaibergen.shop;

import java.util.List;

import kg.kudaibergen.shop.entity.FavoriteShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteShopRepository extends JpaRepository<FavoriteShop, FavoriteShop.Key> {

   boolean existsByUserIdAndShopId(Long userId, Long shopId);

   List<FavoriteShop> findByUserIdOrderByCreatedAtDesc(Long userId);

   long countByUserId(Long userId);

   @Modifying
   @Query(nativeQuery = true, value = "insert into favorite_shops (user_id, shop_id) values (:userId, :shopId) on conflict do nothing")
   void add(@Param("userId") Long userId, @Param("shopId") Long shopId);

   @Modifying
   @Query("delete from FavoriteShop f where f.userId = :userId and f.shopId = :shopId")
   void remove(@Param("userId") Long userId, @Param("shopId") Long shopId);
}
