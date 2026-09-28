package kg.kudaibergen.chat.dto;

/** Бейдж на вкладке «Чаты»: непрочитанные как покупатель и как продавец бокса (0, если бокса нет). */
public record UnreadDto(long asBuyer, long asShop) {
}
