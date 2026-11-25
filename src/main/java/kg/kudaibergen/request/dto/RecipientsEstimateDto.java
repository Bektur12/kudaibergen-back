package kg.kudaibergen.request.dto;

/**
 * «Запрос получат 18 продавцов по Toyota» (06б, 31): пересчитывается при каждом изменении выбора.
 * {@code anyBrand} — продавцов марки на рынке нет, запрос получат все продавцы рынка: «Продавцов Tank пока нет —
 * спросим всех продавцов рынка».
 */
public record RecipientsEstimateDto(int recipients, String brand, boolean anyBrand) {
}
