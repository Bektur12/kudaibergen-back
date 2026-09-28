package kg.kudaibergen.shop;

import java.util.Collection;
import java.util.List;

import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopRepository extends JpaRepository<Shop, Long> {

   /** Кто стоит (или переезжает) в этих контейнерах — для серых клеток экрана 10а. */
   @Query("select s from Shop s where s.containerId in :ids or s.pendingContainerId in :ids")
   List<Shop> findOccupying(@Param("ids") Collection<Long> containerIds);

   @Query("select count(s) > 0 from Shop s where s.containerId = :id or s.pendingContainerId = :id")
   boolean isContainerTaken(@Param("id") Long containerId);

   /** Действующие магазины с фильтрами «Списком» (15) и «Боксу» (06); курсор — по id. */
   @Query("""
         select s from Shop s
         where s.status = :status
           and s.id > :after
           and (:brandId is null or :brandId member of s.brandIds)
           and (:categoryId is null or :categoryId member of s.categoryIds)
           and (:rowId is null or exists (select 1 from Container c where c.id = s.containerId and c.rowId = :rowId))
           and (:query is null or lower(s.name) like :query)
         order by s.id""")
   List<Shop> search(@Param("status") ShopStatus status, @Param("after") long after, @Param("brandId") Long brandId,
                     @Param("categoryId") Long categoryId, @Param("rowId") Long rowId, @Param("query") String query,
                     Pageable page);

   /**
    * Кому может уйти запрос по марке: действующий магазин, тумблер «Бокс закрыт» выключен.
    * Часы работы и ряд/бокс досматривает модуль requests.
    */
   @Query("select s from Shop s where s.status = :status and s.open = true and :brandId member of s.brandIds")
   List<Shop> findReceiving(@Param("status") ShopStatus status, @Param("brandId") Long brandId);

   List<Shop> findByStatusOrderByCreatedAtAsc(ShopStatus status, Pageable page);
}
