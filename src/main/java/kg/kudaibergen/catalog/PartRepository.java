package kg.kudaibergen.catalog;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.catalog.entity.PartStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, Long> {

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select p from Part p where p.id = :id")
   Optional<Part> findForUpdate(@Param("id") Long id);

   long countByShopIdAndStatus(Long shopId, PartStatus status);

   @Query("select count(p) from Part p where p.shopId = :shopId and p.status = :active and p.quantity > 0")
   long countInStock(@Param("shopId") Long shopId, @Param("active") PartStatus active);

   @Query("select count(p) from Part p where p.shopId = :shopId and p.status = :active and p.quantity = 0")
   long countOutOfStock(@Param("shopId") Long shopId, @Param("active") PartStatus active);

   /**
    * «Мои запчасти» (24): новые сверху, курсор — id. status null — все, кроме архива;
    * stock: null — любое наличие, true — в наличии, false — нет. query — по названию и номеру детали.
    */
   @Query("""
         select p from Part p
         where p.shopId = :shopId and p.id < :beforeId
           and ((:status is null and p.status <> :archived) or p.status = :status)
           and (:stock is null or (:stock = true and p.quantity > 0) or (:stock = false and p.quantity = 0))
           and (:query is null or lower(p.title) like :query or p.oemNorm like :oem)
         order by p.id desc""")
   List<Part> findMine(@Param("shopId") Long shopId, @Param("beforeId") long beforeId,
                       @Param("status") PartStatus status, @Param("archived") PartStatus archived,
                       @Param("stock") Boolean stock, @Param("query") String query, @Param("oem") String oem,
                       Pageable page);

   /** Просмотр карточки (дедуп — в Redis): счётчик и дневная статистика одной командой. */
   @Modifying
   @Query(nativeQuery = true, value = """
         with bumped as (update parts set views_count = views_count + 1 where id = :id returning id)
         insert into part_views_daily (part_id, day, views)
         select id, (now() at time zone 'Asia/Bishkek')::date, 1 from bumped
         on conflict (part_id, day) do update set views = part_views_daily.views + 1""")
   void countView(@Param("id") Long id);

   /** Просмотры карточек магазина за последние дни: «посмотрели 214 раз за неделю». */
   @Query(nativeQuery = true, value = """
         select coalesce(sum(v.views), 0) from part_views_daily v
         join parts p on p.id = v.part_id
         where p.shop_id = :shopId and v.day > (now() at time zone 'Asia/Bishkek')::date - :days""")
   long viewsSince(@Param("shopId") Long shopId, @Param("days") int days);
}
