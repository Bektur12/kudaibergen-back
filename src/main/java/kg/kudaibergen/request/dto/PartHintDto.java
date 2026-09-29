package kg.kudaibergen.request.dto;

/** Чип «+ Колодки» (06): id передаётся в hintId запроса, categoryId подставляется в запрос сам. */
public record PartHintDto(Long id, String text, Long categoryId) {
}
