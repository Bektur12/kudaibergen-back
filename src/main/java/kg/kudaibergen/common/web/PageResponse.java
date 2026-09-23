package kg.kudaibergen.common.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Плоский ответ пагинации — клиенту не нужны внутренности Spring Data. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

   public static <T> PageResponse<T> of(Page<T> page) {
      return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
   }

   public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
      return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
            page.getSize(), page.getTotalElements(), page.getTotalPages());
   }
}
