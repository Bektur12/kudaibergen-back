package kg.kudaibergen.delivery;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

/**
 * Доставка Ылдам Экспресс. standardPrice — 1 день до 5 кг (области), remotePrice — 2 дня (Баткенская обл. и
 * отдалённые районы), bishkekPrice — по Бишкеку в тот же день (null — «цену назовёт курьер»). Доплату за вес
 * и размер считает курьер. cutoff — до этого часа (Бишкек) курьер заберёт посылку сегодня.
 * courierWhatsapp — номер для wa.me (без +), courierPhone — кнопка «Курьер Ылдам».
 */
@ConfigurationProperties(prefix = "app.delivery")
public record DeliveryProperties(String courierName, @Nullable Integer bishkekPrice, int standardPrice,
                                 int remotePrice, LocalTime cutoff, @Nullable String courierWhatsapp,
                                 @Nullable String courierPhone) {
}
