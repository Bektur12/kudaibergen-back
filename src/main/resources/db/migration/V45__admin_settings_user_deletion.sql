-- ─────────────────────────── НАСТРОЙКИ АДМИНКИ ───────────────────────────
-- Флажки, которые админка переключает без передеплоя. Сейчас — авто-активация продавцов и мастеров при
-- регистрации (sellers.auto_approve, masters.auto_approve). Строки нет — действует SHOP_VERIFICATION_REQUIRED.
CREATE TABLE app_settings (
    key         VARCHAR(60)  PRIMARY KEY,
    value       VARCHAR(200) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL
);

-- ─────────────────────────── УДАЛЕНИЕ ПОЛЬЗОВАТЕЛЯ ───────────────────────────
-- Заказы с доставкой держали покупателя и магазин без ON DELETE: аккаунт с заказом нельзя было стереть
-- ни из админки, ни ночной джобой через 30 дней после «Удалить аккаунт». Уходят вместе с ними, как и чаты.
ALTER TABLE delivery_orders
    DROP CONSTRAINT delivery_orders_buyer_id_fkey,
    ADD CONSTRAINT delivery_orders_buyer_id_fkey FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
    DROP CONSTRAINT delivery_orders_shop_id_fkey,
    ADD CONSTRAINT delivery_orders_shop_id_fkey FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;
