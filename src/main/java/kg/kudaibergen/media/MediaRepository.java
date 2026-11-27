package kg.kudaibergen.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<Media, Long> {

   /**
    * Фото, которые так и не прикрепили (или открепили) и которые старше порога: ни товар (фото и видео), ни фото места,
    * ни аватар магазина или пользователя, ни запрос или ответ «Есть» (фото и голосовые), ни заявка или отклик мастера, ни карточка товара в чате
    * и голосовое, скопированное в чат (ответ «Есть», отклик мастера), на них не ссылаются.
    */
   @Query(nativeQuery = true, value = """
         select m.* from media m
         where m.created_at < :before
           and not exists (select 1 from part_photos pp where pp.media_id = m.id)
           and not exists (select 1 from parts pv where pv.video_media_id = m.id)
           and not exists (select 1 from shop_photos sp where sp.media_id = m.id)
           and not exists (select 1 from shops s where s.avatar_media_id = m.id)
           and not exists (select 1 from users u where u.avatar_media_id = m.id)
           and not exists (select 1 from request_photos rp where rp.media_id = m.id)
           and not exists (select 1 from reply_photos rep where rep.media_id = m.id)
           and not exists (select 1 from masters ms where ms.avatar_media_id = m.id)
           and not exists (select 1 from master_photos mp where mp.media_id = m.id)
           and not exists (select 1 from service_request_photos sp2 where sp2.media_id = m.id)
           and not exists (select 1 from part_requests pr where pr.voice_media_id = m.id)
           and not exists (select 1 from request_replies rr where rr.voice_media_id = m.id)
           and not exists (select 1 from service_requests sr where sr.voice_media_id = m.id)
           and not exists (select 1 from service_offers so where so.voice_media_id = m.id)
           and not exists (select 1 from messages msg
                           where msg.type = 'PART' and (msg.payload ->> 'mediaId')::bigint = m.id)
           and not exists (select 1 from messages vm where m.file_key is not null and vm.media_key = m.file_key)
         order by m.id
         limit :limit""")
   List<Media> findOrphans(@Param("before") Instant before, @Param("limit") int limit);

   /** Следующее видео на пережатие: сначала новые — их сейчас смотрят в ленте. */
   @Query(nativeQuery = true, value = """
         select m.* from media m
         where m.transcode_state = 'PENDING' and m.kind = 'VIDEO'
         order by m.id desc
         limit 1""")
   Optional<Media> nextToTranscode();

   /** Попытка пережатия: maxAttempts-я неудачная — FAILED, видео остаётся исходным. */
   @Modifying
   @Query(nativeQuery = true, value = """
         update media set transcode_attempts = transcode_attempts + 1,
                          transcode_state = case when transcode_attempts + 1 >= :maxAttempts then 'FAILED'
                                                 else transcode_state end
         where id = :id""")
   int countTranscodeFailure(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

   /** Пережимать не нужно (исходник и так лёгкий MP4). */
   @Modifying
   @Query(nativeQuery = true, value = "update media set transcode_state = 'DONE' where id = :id")
   int markTranscoded(@Param("id") Long id);

   /**
    * Подмена файла видео на пережатый; исходник — в original_key. Строку успели изменить или удалить
    * (file_key уже не oldKey) — 0, новый файл не нужен.
    */
   @Modifying
   @Query(nativeQuery = true, value = """
         update media set original_key = file_key, file_key = :newKey, mime_type = 'video/mp4',
                          size_bytes = :size, transcode_state = 'DONE'
         where id = :id and file_key = :oldKey""")
   int replaceVideoFile(@Param("id") Long id, @Param("oldKey") String oldKey, @Param("newKey") String newKey,
                        @Param("size") long size);

   /** Сообщения чата ссылаются на файл ключом — переводим на пережатый, иначе чистка сочтёт видео ничьим. */
   @Modifying
   @Query(nativeQuery = true, value = "update messages set media_key = :newKey where media_key = :oldKey")
   int repointMessages(@Param("oldKey") String oldKey, @Param("newKey") String newKey);
}
