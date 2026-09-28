package kg.kudaibergen.catalog.dto;

/** Чипы «Все · 38», «В наличии · 32», «Нет · 6», черновики, архив и «посмотрели N раз за неделю» (24). */
public record MyPartsSummaryDto(long all, long inStock, long outOfStock, long drafts, long archived,
                                long viewsWeek, long activeLimit) {
}
