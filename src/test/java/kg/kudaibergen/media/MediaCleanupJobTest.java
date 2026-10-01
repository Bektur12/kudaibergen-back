package kg.kudaibergen.media;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import kg.kudaibergen.media.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaCleanupJobTest {

   private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");

   @Test
   void удаляетФайлыИСтрокуАСбойОставляетНаПотом() {
      MediaRepository repository = mock(MediaRepository.class);
      MediaStorage storage = mock(MediaStorage.class);
      Media ok = media(1L, "part/a-1080.jpg", "part/a-320.jpg");
      Media broken = media(2L, "part/b-1080.jpg", "part/b-320.jpg");
      when(repository.findOrphans(any(), anyInt())).thenReturn(List.of(ok, broken));
      doThrow(new IllegalStateException("S3 недоступен")).when(storage).delete("part/b-1080.jpg");

      int removed = new MediaCleanupJob(repository, storage, Clock.fixed(NOW, ZoneOffset.UTC)).cleanup();

      assertThat(removed).isEqualTo(1);
      verify(repository).findOrphans(NOW.minus(MediaCleanupJob.GRACE), 500);
      verify(storage).delete("part/a-1080.jpg");
      verify(storage).delete("part/a-320.jpg");
      verify(repository).deleteById(1L);
      verify(repository, never()).deleteById(2L);
   }

   private static Media media(long id, String large, String thumb) {
      Media media = new Media(5L, MediaPurpose.PART, large, thumb, 1080, 810, 1000);
      ReflectionTestUtils.setField(media, "id", id);
      return media;
   }
}
