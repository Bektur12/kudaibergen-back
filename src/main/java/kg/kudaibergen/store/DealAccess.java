package kg.kudaibergen.store;

/**
 * «Есть ли между покупателем и магазином сделка» (= открыт чат).
 * Нужно, чтобы не отдавать телефоны филиалов кому попало (п.8 ТЗ).
 * Реализация живёт в пакете chat — здесь только контракт, без обратной зависимости.
 */
public interface DealAccess {

   boolean hasAcceptedDeal(Long buyerId, Long storeId);
}
