package kg.kudaibergen.catalog.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** «Написать комментарий» под видео. */
public record PartCommentInput(
      @NotBlank(message = "Напишите комментарий") @Size(max = 500, message = "Не длиннее 500 символов") String text) {
}
