package kg.kudaibergen.chat.dto;

/** Бейдж на вкладке «Чаты»: непрочитанные как покупатель, как продавец бокса и как мастер (0, если нет). */
public record UnreadDto(long asBuyer, long asShop, long asMaster) {
}
