package kg.kudaibergen.store;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.store.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

   Optional<Store> findByOwnerId(Long ownerId);

   /**
    * Кому улетит запрос: магазины, которые торгуют этой категорией и имеют хотя бы один
    * филиал (т.е. реально работают). Без фильтра по городу — продукт сейчас только в
    * одном городе, матчинг по городу только терял бы релевантные магазины на плохих данных
    * филиала (пустой/неверный city). Это главный запрос продукта — он не должен спамить
    * нерелевантные (по категории) магазины.
    */
   @Query(value = """
         SELECT DISTINCT sc.store_id
         FROM store_categories sc
         JOIN stores s         ON s.id = sc.store_id
         JOIN store_branches b ON b.store_id = s.id
         WHERE sc.category = :category
           AND s.verification_status <> 'BLOCKED'
         """, nativeQuery = true)
   List<Long> findMatchingStoreIds(@Param("category") String category);

   /** Владельцы магазинов — им уйдут пуши о новом запросе. */
   @Query("select s.ownerUserId from Store s where s.id in :storeIds")
   List<Long> findOwnerUserIds(@Param("storeIds") List<Long> storeIds);

   @Query("select s.name from Store s where s.id = :storeId")
   String findName(@Param("storeId") Long storeId);

   @Query(value = """
         SELECT DISTINCT s.* FROM stores s
         LEFT JOIN store_categories sc ON sc.store_id = s.id
         LEFT JOIN store_branches b    ON b.store_id = s.id
         WHERE s.verification_status <> 'BLOCKED'
           AND (CAST(:category AS varchar) IS NULL OR sc.category = CAST(:category AS varchar))
           AND (CAST(:city AS varchar) IS NULL OR b.city = CAST(:city AS varchar))
         ORDER BY s.rating DESC, s.id
         """,
         countQuery = """
               SELECT COUNT(DISTINCT s.id) FROM stores s
               LEFT JOIN store_categories sc ON sc.store_id = s.id
               LEFT JOIN store_branches b    ON b.store_id = s.id
               WHERE s.verification_status <> 'BLOCKED'
                 AND (CAST(:category AS varchar) IS NULL OR sc.category = CAST(:category AS varchar))
                 AND (CAST(:city AS varchar) IS NULL OR b.city = CAST(:city AS varchar))
               """,
         nativeQuery = true)
   Page<Store> search(@Param("category") String category, @Param("city") String city, Pageable pageable);
}
