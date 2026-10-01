package kg.kudaibergen.common.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import kg.kudaibergen.common.error.BadRequestException;
import org.springframework.lang.Nullable;

/**
 * Страница с курсором: {items, nextCursor}. nextCursor = null — дальше ничего нет.
 * Курсор непрозрачный для клиента (base64), внутри — ключ сортировки последнего элемента.
 */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {

   public static final int DEFAULT_LIMIT = 20;
   public static final int MAX_LIMIT = 100;

   /** Из выборки limit + 1 строк: лишняя строка значит «есть продолжение». */
   public static <E, T> CursorPage<T> of(List<E> rows, int limit, Function<E, String> key, Function<E, T> mapper) {
      boolean more = rows.size() > limit;
      List<E> page = more ? rows.subList(0, limit) : rows;
      String next = more ? encode(key.apply(page.get(page.size() - 1))) : null;
      return new CursorPage<>(page.stream().map(mapper).toList(), next);
   }

   public static int limit(Integer requested) {
      if (requested == null) {
         return DEFAULT_LIMIT;
      }
      return Math.max(1, Math.min(MAX_LIMIT, requested));
   }

   public static String encode(String key) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
   }

   public static String decode(String cursor) {
      try {
         return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
         throw new BadRequestException("BAD_CURSOR", "Некорректный курсор");
      }
   }

   /** Курсор по числовому id (0 — с начала). */
   public static long afterId(String cursor) {
      if (cursor == null || cursor.isBlank()) {
         return 0;
      }
      try {
         return Long.parseLong(decode(cursor));
      } catch (NumberFormatException e) {
         throw new BadRequestException("BAD_CURSOR", "Некорректный курсор");
      }
   }
}
