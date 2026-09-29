package kg.kudaibergen.request;

import java.util.List;

import kg.kudaibergen.request.entity.PartHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartHintRepository extends JpaRepository<PartHint, Long> {

   /**
    * Подсказки для машины (06): чаще всего выбранные в запросах за 90 дней — запрос по той же модели
    * весит 3, по той же марке 1; дальше базовая популярность. Без машины brandId = modelId = 0.
    */
   @Query(nativeQuery = true, value = """
         select h.* from part_hints h
         left join part_requests r on r.hint_id = h.id and r.brand_id = :brandId
                                  and r.created_at > now() - interval '90 days'
         group by h.id
         order by coalesce(sum(case when r.model_id = :modelId then 3 else 1 end)
                           filter (where r.id is not null), 0) desc,
                  h.popularity desc, h.id
         limit :limit""")
   List<PartHint> topFor(@Param("brandId") long brandId, @Param("modelId") long modelId, @Param("limit") int limit);
}
