package kg.kudaibergen.shop.stock;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopStockRepository extends JpaRepository<ShopStockItem, Long> {

   List<ShopStockItem> findByShopIdOrderByIdAsc(Long shopId);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query("delete from ShopStockItem i where i.shopId = :shopId")
   void deleteAllOfShop(@Param("shopId") Long shopId);

   /**
    * У кого деталь «обычно есть» — id действующих магазинов в порядке показа. Без машины brandId = modelId = 0.
    * Строка без марки подходит, если магазин вообще продаёт эту марку (shop_brands). Порядок: сначала без пометки
    * «ложное Есть», дальше точнее совпадение (модель → марка → все марки), быстрее отвечает, выше рейтинг.
    * marketId: -1 — все, 0 — вне рынков, иначе рынок.
    */
   @Query(nativeQuery = true, value = """
         select s.id from shop_stock st
         join shops s on s.id = st.shop_id
         where st.hint_id = :hintId
           and s.status = 'ACTIVE'
           and (case
                  when st.brand_id is null then :brandId = 0 or exists (
                       select 1 from shop_brands sb where sb.shop_id = s.id and sb.brand_id = :brandId)
                  else :brandId = 0 or st.brand_id = :brandId
                end)
           and (st.model_id is null or :modelId = 0 or st.model_id = :modelId)
           and (:marketId = -1 or (:marketId = 0 and s.market_id is null) or s.market_id = :marketId)
         group by s.id, s.false_have_since, s.reply_minutes, s.rating
         order by (s.false_have_since is not null),
                  max(case when :modelId <> 0 and st.model_id = :modelId then 3
                           when :brandId <> 0 and st.brand_id = :brandId then 2
                           else 1 end) desc,
                  s.reply_minutes asc nulls last,
                  s.rating desc,
                  s.id""")
   List<Long> stockingShopIds(@Param("hintId") long hintId, @Param("brandId") long brandId,
                              @Param("modelId") long modelId, @Param("marketId") long marketId);

   /**
    * Подсказки «добавить в список»: на что магазин ответил «Есть» не меньше трёх раз за 60 дней, а в списке
    * этого нет (ни на эту марку, ни на все марки).
    */
   @Query(nativeQuery = true, value = """
         select r.hint_id as hintId, r.brand_id as brandId, count(*) as answers
         from request_replies rr
         join part_requests r on r.id = rr.request_id
         join part_hints h on h.id = r.hint_id and h.is_active
         where rr.shop_id = :shopId
           and rr.answer = 'HAVE'
           and rr.created_at > now() - interval '60 days'
           and not exists (select 1 from shop_stock st
                           where st.shop_id = :shopId and st.hint_id = r.hint_id
                             and (st.brand_id is null or st.brand_id = r.brand_id))
         group by r.hint_id, r.brand_id
         having count(*) >= 3
         order by count(*) desc, r.hint_id
         limit 10""")
   List<SuggestionRow> suggestions(@Param("shopId") Long shopId);

   interface SuggestionRow {
      Long getHintId();

      Long getBrandId();

      long getAnswers();
   }
}
