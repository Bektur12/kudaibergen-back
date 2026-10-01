package kg.kudaibergen.request.dto;

import kg.kudaibergen.request.entity.ReviewTag;

/** Тег оценки на языке запроса. */
public record ReviewTagDto(ReviewTag code, String label) {
}
