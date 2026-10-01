package kg.kudaibergen.request.dto;

/** «Запрос получат 18 продавцов по Toyota» (06б, 31): пересчитывается при каждом изменении выбора. */
public record RecipientsEstimateDto(int recipients, String brand) {
}
