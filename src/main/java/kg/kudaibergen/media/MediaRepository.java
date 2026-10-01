package kg.kudaibergen.media;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<Media, Long> {

   /**
    * Фото, которые так и не прикрепили (или открепили) и которые старше порога: ни товар, ни фото места,
    * ни аватар магазина или пользователя, ни запрос или ответ «Есть», ни карточка товара в чате на них не ссылаются.
    */
   @Query(nativeQuery = true, value = """
         select m.* from media m
         where m.created_at < :before
           and not exists (select 1 from part_photos pp where pp.media_id = m.id)
           and not exists (select 1 from shop_photos sp where sp.media_id = m.id)
           and not exists (select 1 from shops s where s.avatar_media_id = m.id)
           and not exists (select 1 from users u where u.avatar_media_id = m.id)
           and not exists (select 1 from request_photos rp where rp.media_id = m.id)
           and not exists (select 1 from reply_photos rep where rep.media_id = m.id)
           and not exists (select 1 from masters ms where ms.avatar_media_id = m.id)
           and not exists (select 1 from master_photos mp where mp.media_id = m.id)
           and not exists (select 1 from service_request_photos sp2 where sp2.media_id = m.id)
           and not exists (select 1 from messages msg
                           where msg.type = 'PART' and (msg.payload ->> 'mediaId')::bigint = m.id)
         order by m.id
         limit :limit""")
   List<Media> findOrphans(@Param("before") Instant before, @Param("limit") int limit);
}
